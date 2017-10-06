import java.util.ArrayList;
import java.util.HashSet;

public class TxHandler {

    private UTXOPool P;

    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
     * constructor.
     */
    public TxHandler(UTXOPool utxoPool) {
        P = new UTXOPool(utxoPool);
    }

    public UTXOPool getUTXOPool() {
        return P;
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
        ArrayList<Transaction.Input> inputs = tx.getInputs();
        ArrayList<Transaction.Output> outputs = tx.getOutputs();

        double inputSum = 0, outputSum = 0;

        HashSet<UTXO> claimed = new HashSet<UTXO>();

        for (int i = 0; i < inputs.size(); i++) {
            Transaction.Input input = inputs.get(i);
            UTXO u = new UTXO(input.prevTxHash, input.outputIndex);

            // check if all outputs are in the UTXO pool or if UTXO is already claimed
            if (!P.contains(u) || claimed.contains(u)) return false;

            Transaction.Output prevOutput = P.getTxOutput(u);

            // verify the signatures
            byte[] rawData = tx.getRawDataToSign(i);
            if (!Crypto.verifySignature(prevOutput.address, rawData, input.signature)) return false;

            inputSum += prevOutput.value;

            claimed.add(u);
        }

        for (Transaction.Output output : outputs) {
            // verify non-negative values
            if (output.value < 0) return false;
            outputSum += output.value;
        }

        // verify the sum of input values is greater than that of output values
        return inputSum >= outputSum;
    }

    /**
     * Handles each epoch by receiving an unordered array of proposed transactions, checking each
     * transaction for correctness, returning a mutually valid array of accepted transactions, and
     * updating the current UTXO pool as appropriate.
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {
        ArrayList<Transaction> acceptedTxs = new ArrayList<Transaction>();

        for (Transaction tx : possibleTxs) {
            if (!isValidTx(tx)) continue;
            accept(tx);
            acceptedTxs.add(tx);
        }

        return acceptedTxs.toArray(new Transaction[acceptedTxs.size()]);
    }

    /**
     * Helper method used to accept a given transaction.
     */
    private void accept(Transaction tx) {
        ArrayList<Transaction.Input> inputs = tx.getInputs();
        ArrayList<Transaction.Output> outputs = tx.getOutputs();

        for (Transaction.Input input : inputs) {
            UTXO u = new UTXO(input.prevTxHash, input.outputIndex);
            P.removeUTXO(u);
        }

        for (int i = 0; i < outputs.size(); i++) {
            Transaction.Output output = outputs.get(i);
            UTXO newOutput = new UTXO(tx.getHash(), i);
            P.addUTXO(newOutput, output);
        }
    }
}
