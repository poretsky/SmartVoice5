package orestes.bloomfilter;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.List;

/**
 * Represents a Bloom filter and provides default methods for hashing.
 */
public abstract class BloomFilter<T> implements Cloneable {

    /**
     * Adds the passed value to the filter.
     * 
     * @param element value to add
     * @return {@code true} if the value did not previously exist in the filter. Note, that a false positive may occur,
     * thus the value may not have already been in the filter, but it hashed to a set of bits already in the filter.
     */
    public abstract boolean add(byte[] element);

    /**
     * Adds the passed value to the filter.
     * 
     * @param element value to add
     * @return {@code true} if the value did not previously exist in the filter. Note, that a false positive may occur,
     * thus the value may not have already been in the filter, but it hashed to a set of bits already in the filter.
     */
    public boolean add(T element) {
        return add(toBytes(element));
    }

    /**
     * Performs a bulk add operation for a collection of elements.
     * 
     * @param elements
     * @return a list of booleans indicating for each element, whether it was previously present in the filter
     */
    public List<Boolean> addAll(Collection<T> elements) {
        ArrayList<Boolean> newList = new ArrayList<Boolean>(elements.size());
        for (T t : elements) {
            newList.add(this.add(t));
        }
        return newList;
    }

    /**
     * Removes all elements from the filter (i.e. resets all bits to zero).
     */
    public abstract void clear();

    /**
     * Tests whether an element is present in the filter (subject to the specified false positive rate).
     * 
     * @param element
     * @return {@code true} if the element is contained
     */
    public abstract boolean contains(byte[] element);

    /**
     * Tests whether an element is present in the filter (subject to the specified false positive rate).
     * 
     * @param element
     * @return {@code true} if the element is contained
     */
    public boolean contains(T element) {
        return contains(toBytes(element));
    }

    /**
     * Bulk-tests elements for existence in the filter.
     * 
     * @param elements a collection of elements to test
     * @return a list of booleans indicating for each element, whether it is present in the filter
     */
    public List<Boolean> contains(Collection<T> elements) {
        ArrayList<Boolean> newList = new ArrayList<Boolean>(elements.size());
        for (T t : elements) {
            newList.add(this.contains(t));
        }
        return newList;
    }

    /**
     * Bulk-tests elements for existence in the filter.
     * 
     * @param elements a collection of elements to test
     * @return {@code true} if all elements are present in the filter
     */
    public boolean containsAll(Collection<T> elements) {
        for (T t : elements) {
            if (!this.contains(t)) return false;
        }
        return true;
    }

    /**
     * Return the underyling bit vector of the Bloom filter.
     * 
     * @return the underyling bit vector of the Bloom filter.
     */
    public abstract BitSet getBitSet();

    /**
     * Returns the configuration/builder of the Bloom filter.
     * 
     * @return the configuration/builder of the Bloom filter.
     */
    public abstract FilterBuilder config();

    /**
     * Constructs a deep copy of the Bloom filter
     * 
     * @return a cloned Bloom filter
     */
    @SuppressWarnings("unchecked")
    @Override
    public BloomFilter<T> clone() {
        try {
            return (BloomFilter<T>) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Return the size of the Bloom filter, i.e. the number of positions in the underlyling bit vector (called m in the
     * literature).
     * 
     * @return the bit vector size
     */
    public int getSize() {
        return config().size();
    }

    /**
     * Returns the expected number of elements (called n in the literature)
     * 
     * @return the expected number of elements
     */
    public int getExpectedElements() {
        return config().expectedElements();
    }

    /**
     * Returns the number of hash functions (called k in the literature)
     * 
     * @return the number of hash functions
     */
    public int getHashes() {
        return config().hashes();
    }

    /**
     * Returns the expected false positive probability for the expected amounts of elements. This is independent of the
     * actual amount of elements in the filter. Use {@link #getFalsePositiveProbability(double)} for that purpose.
     * 
     * @return the static expected false positive probability
     */
    public double getFalsePositiveProbability() {
        return config().falsePositiveProbability();
    }

    /**
     * Converts an element to the byte array representation used for hashing.
     * 
     * @param element the element to convert
     * @return the elements byte array representation
     */
    public byte[] toBytes(T element) {
        return element.toString().getBytes(FilterBuilder.defaultCharset());
    }

    /**
     * Checks if two Bloom filters are compatible, i.e. have compatible parameters (hash function, size, etc.)
     * 
     * @param bloomFilter
     * @param other
     * @return
     */
    public boolean compatible(BloomFilter<T> bloomFilter, BloomFilter<T> other) {
        return bloomFilter.config().isCompatibleTo(other.config());
    }

    /**
     * Destroys the Bloom filter by deleting its contents and metadata
     */
    public void remove() {
        clear();
    }

    /**
     * Returns the k hash values for an inputs element in byte array form
     * 
     * @param bytes input element
     * @return hash values
     */
    public int[] hash(byte[] bytes) {
        return config().hashFunction().hash(bytes, config().size(), config().hashes());
    }

    /**
     * Dispatches the hash function for a string value
     * 
     * @param value the value to be hashed
     * @return array with <i>hashes</i> integer hash positions in the range <i>[0,size)</i>
     */
    public int[] hash(String value) {
        return hash(value.getBytes(FilterBuilder.defaultCharset()));
    }

    /**
     * Performs the union operation on two compatible bloom filters. This is achieved through a bitwise OR operation on
     * their bit vectors. This operations is lossless, i.e. no elements are lost and the bloom filter is the same that
     * would have resulted if all elements wer directly inserted in just one bloom filter.
     * 
     * @param other the other bloom filter
     * @return <tt>true</tt> if this bloom filter could successfully be updated through the union with the provided
     * bloom filter
     */
    public abstract boolean union(BloomFilter<T> other);

    /**
     * Performs the intersection operation on two compatible bloom filters. This is achieved through a bitwise AND
     * operation on their bit vectors. The operations doesn't introduce any false negatives but it does raise the false
     * positive probability. The the false positive probability in the resulting Bloom filter is at most the
     * false-positive probability in one of the constituent bloom filters
     * 
     * @param other the other bloom filter
     * @return <tt>true</tt> if this bloom filter could successfully be updated through the intersection with the
     * provided bloom filter
     */
    public abstract boolean intersect(BloomFilter<T> other);

    /**
     * Returns {@code true} if the Bloom filter does not contain any elements
     * 
     * @return {@code true} if the Bloom filter does not contain any elements
     */
    public abstract boolean isEmpty();

    /**
     * Returns the probability of a false positive (approximated): <br> <code>(1 - e^(-hashes * insertedElements /
     * size)) ^ hashes</code>
     * 
     * @param insertedElements The number of elements already inserted into the Bloomfilter
     * @return probability of a false positive after <i>expectedElements</i> {@link #add(byte[])} operations
     */
    public double getFalsePositiveProbability(double insertedElements) {
        return FilterBuilder.optimalP(config().hashes(), config().size(), insertedElements);
    }

    /**
     * Returns the probability of a false positive (approximated) using an estimation of how many elements are currently in the filter
     *
     * @return probability of a false positive
     */
    public double getEstimatedFalsePositiveProbability() {
        return getFalsePositiveProbability(getEstimatedPopulation());
    }

    /**
     * Calculates the numbers of Bits per element, based on the expected number of inserted elements
     * <i>expectedElements</i>.
     * 
     * @param n The number of elements already inserted into the Bloomfilter
     * @return The numbers of bits per element
     */
    public double getBitsPerElement(int n) {
        return config().size() / (double) n;
    }

    /**
     * Returns the probability that a bit is zero.
     * 
     * @param n The number of elements already inserted into the Bloomfilter
     * @return probability that a certain bit is zero after <i>expectedElements</i> {@link #add(byte[])} operations
     */
    public double getBitZeroProbability(int n) {
        return Math.pow(1 - (double) 1 / config().size(), config().hashes() * n);
    }

    /**
     * Estimates the current population of the Bloom filter (see: http://en.wikipedia.org/wiki/Bloom_filter#Approximating_the_number_of_items_in_a_Bloom_filter
     * )
     *
     * @return the estimated amount of elements in the filter
     */
    public Double getEstimatedPopulation() {
        return population(getBitSet(), config());
    }

    public static Double population(BitSet bitSet, FilterBuilder config) {
        int oneBits = bitSet.cardinality();
        return -config.size() / ((double) config.hashes()) * Math.log(1 - oneBits / ((double) config.size()));
    }

    /**
     * Prints the Bloom filter: metadata and data
     * 
     * @return String representation of the Bloom filter
     */
    public String asString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Bloom Filter Parameters: ");
        sb.append("size = " + config().size() + ", ");
        sb.append("hashes = " + config().hashes() + ", ");
        sb.append("Bits: " + getBitSet().toString());
        return sb.toString();
    }

}
