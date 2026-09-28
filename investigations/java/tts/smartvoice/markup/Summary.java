package tts.smartvoice.markup;

class Summary {

    long hits;
    long refutes;
    long total;
    boolean misses;

    void report(String title) {
        System.out.printf("%s: %d hits (%d%%), %d refutes (%d%%)\n", title, hits, percentage(hits), refutes, percentage(refutes));
    }

    private long percentage(long value) {
        return (total != 0) ?
            (((value * 100) + (total >> 1)) / total) :
            0;
    }

}
