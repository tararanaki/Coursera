package ch.scrooge;

import java.util.*;

public class MaxFeeTxHandler {

    private UTXOPool utxoPool;

    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
     * constructor.
     */
    public MaxFeeTxHandler(UTXOPool utxoPool) {
        this.utxoPool = utxoPool;
    }

    /**
     * @return true if:
     * (1) all outputs claimed by {@code tx} are in the current UTXO pool, 
     * (2) the signatures on each input of {@code tx} are valid, 
     * (3) no UTXO is claimed multiple times by {@code tx},
     * (4) all of {@code tx}s output values are non-negative, and
     * (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
     *     values; and false otherwise.
     */
    public boolean isValidTx(Transaction tx) {
        double inputSum = 0;
        double outputSum = 0;
        UTXOPool uniqueUTXO = new UTXOPool();
        for (int i = 0; i < tx.getInputs().size(); i++) {
            Transaction.Input input = tx.getInput(i);
            UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);
            if (!utxoPool.contains(utxo)) {
                // 1
                return false;
            }
            Transaction.Output previousOutput = utxoPool.getTxOutput(utxo);
            if (!Crypto.verifySignature(previousOutput.address, tx.getRawDataToSign(i), input.signature)) {
                //2
                return false;
            }

            if (uniqueUTXO.contains(utxo)) {
                //3
                return false;
            } else {
                uniqueUTXO.addUTXO(utxo, previousOutput);
            }
            inputSum += previousOutput.value;

        }

        ArrayList<Transaction.Output> outputs = tx.getOutputs();
        for (Transaction.Output output : outputs) {
            if (output.value < 0) {
                //4
                return false;
            }

            outputSum += output.value;
        }

        //5
        return !(inputSum < outputSum);
    }

    /**
     * Handles each epoch by receiving an unordered array of proposed transactions,
     * ordering them according to the potential transaction feed received,
     * checking each transaction for correctness, returning a mutually valid
     * array of accepted transactions resulting in a maximum fee, and
     * updating the current UTXO pool as appropriate.
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {
        List<Transaction> possibleTxsList = Arrays.asList(possibleTxs);
        possibleTxsList.sort(Comparator.comparingDouble(this::getFees));

        List<Transaction> validTransactions = new ArrayList<Transaction>();

        for (Transaction possibleTx : possibleTxsList) {
            if (isValidTx(possibleTx)) {
                validTransactions.add(possibleTx);
                for (Transaction.Input input : possibleTx.getInputs()) {
                    utxoPool.removeUTXO(new UTXO(input.prevTxHash, input.outputIndex));
                }

                ArrayList<Transaction.Output> outputs = possibleTx.getOutputs();
                for (int i = 0; i < outputs.size(); i++) {
                    Transaction.Output output = outputs.get(i);
                    utxoPool.addUTXO(new UTXO(possibleTx.getHash(), i), output);
                }
            }
        }

        return validTransactions.toArray(new Transaction[validTransactions.size()]);
    }

    private double getFees(Transaction tx) {
        double inputSum = 0;
        double outputSum = 0;
        for (int i = 0; i < tx.getInputs().size(); i++) {
            Transaction.Input input = tx.getInput(i);
            UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);
            if (!utxoPool.contains(utxo)) {
                // 1
                return 0.0;
            }
            Transaction.Output previousOutput = utxoPool.getTxOutput(utxo);
            inputSum += previousOutput.value;
        }

        ArrayList<Transaction.Output> outputs = tx.getOutputs();
        for (Transaction.Output output : outputs) {
            outputSum += output.value;
        }

        return inputSum - outputSum;
    }

}
