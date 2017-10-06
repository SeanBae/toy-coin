// Block Chain should maintain only limited block nodes to satisfy the functions
// You should not have all the blocks added to the block chain in memory 
// as it would cause a memory overflow.

import java.util.ArrayList;
import java.util.HashMap;

public class BlockChain {

    private class Node {
        private Block block;
        private Node prev;
        private UTXOPool utxoPool;
        private int height;

        private Node(Block block, Node prev, UTXOPool utxoPool) {
            this.block = block;
            this.prev = prev;
            this.utxoPool = utxoPool;
            this.height = (prev == null) ? 1 : prev.height + 1;
        }
    }

    public static final int CUT_OFF_AGE = 10;

    private HashMap<ByteArrayWrapper, Node> chain;
    private Node maxHeightNode;
    private TransactionPool txPool;

    /**
     * create an empty block chain with just a genesis block. Assume {@code genesisBlock} is a valid
     * block
     */
    public BlockChain(Block genesisBlock) {
        UTXOPool utxoPool = new UTXOPool();
        processCoinbaseTransaction(genesisBlock.getCoinbase(), utxoPool);

        Node genesisNode = new Node(genesisBlock, null, utxoPool);

        chain = new HashMap<ByteArrayWrapper, Node>();
        chain.put(new ByteArrayWrapper(genesisBlock.getHash()), genesisNode);

        txPool = new TransactionPool();
        maxHeightNode = genesisNode;
    }

    /** Get the maximum height block */
    public Block getMaxHeightBlock() {
        return maxHeightNode.block;
    }

    /** Get the UTXOPool for mining a new block on top of max height block */
    public UTXOPool getMaxHeightUTXOPool() {
        return maxHeightNode.utxoPool;
    }

    /** Get the transaction pool to mine a new block */
    public TransactionPool getTransactionPool() {
        return txPool;
    }

    /**
     * Add {@code block} to the block chain if it is valid. For validity, all transactions should be
     * valid and block should be at {@code height > (maxHeight - CUT_OFF_AGE)}.
     * 
     * <p>
     * For example, you can try creating a new block over the genesis block (block height 2) if the
     * block chain height is {@code <= CUT_OFF_AGE + 1}.
     * As soon as {@code height > CUT_OFF_AGE + 1}, you cannot create a new block at height 2.
     * 
     * @return true if block is successfully added
     */
    public boolean addBlock(Block block) {
        byte[] prevBlockHash = block.getPrevBlockHash();
        if (prevBlockHash == null) return false;

        Node prevNode = chain.get(new ByteArrayWrapper(prevBlockHash));
        if (prevNode == null || prevNode.height < maxHeightNode.height - CUT_OFF_AGE) return false;

        TxHandler txHandler = new TxHandler(prevNode.utxoPool);
        ArrayList<Transaction> transactions = block.getTransactions();

        Transaction[] validTxs = txHandler.handleTxs(transactions.toArray(new Transaction[transactions.size()]));
        if (validTxs.length != transactions.size()) return false;

        UTXOPool utxoPool = txHandler.getUTXOPool();
        processCoinbaseTransaction(block.getCoinbase(), utxoPool);

        Node newNode = new Node(block, prevNode, utxoPool);
        maxHeightNode = (newNode.height > maxHeightNode.height) ? newNode : maxHeightNode;
        chain.put(new ByteArrayWrapper(block.getHash()), newNode);

        return true;
    }

    /** Add a transaction to the transaction pool */
    public void addTransaction(Transaction tx) {
        txPool.addTransaction(tx);
    }

    private void processCoinbaseTransaction(Transaction coinbase, UTXOPool utxoPool) {
        for (int i = 0; i < coinbase.numOutputs(); i++) {
            Transaction.Output output = coinbase.getOutput(i);
            UTXO u = new UTXO(coinbase.getHash(), i);
            utxoPool.addUTXO(u, output);
        }
    }
}