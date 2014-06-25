package fr.ina.dlweb.proprioception.facetRedux.job;

import fr.ina.dlweb.mapreduce.job.JobManager;
import fr.ina.dlweb.utils.ArgsParser;
import fr.ina.dlweb.utils.MathUtils;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.io.ArrayPrimitiveWritable;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.RawComparator;
import org.apache.hadoop.io.WritableComparator;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;
import org.apache.hadoop.mapreduce.lib.partition.HashPartitioner;
import org.apache.hadoop.util.Tool;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * key  : status|date|site|contentCategory|TLD|sizeCategory|level
 * value: metaCount, urlCount, shaCount, metaSize, realSize
 *
 * @author drapin
 */
public abstract class FacetReduxJob extends Configured implements Tool
{
    public static final String YEAR_KEY = "fr.ina.dlweb.facetRedux.year";

    public static final String COMBINATION_TYPE_KEY = "fr.ina.dlweb.facetRedux.combinationType";
    public static enum CombinationType
    {
        normal,
        explicitDate,
        wildcardDate,
    }

    protected static final Charset US_ASCII = Charset.forName("US-ASCII");
    public static final Set<String> STATUSES = new HashSet<String>();
    public static final String[] META_KEYS = new String[]{
        "date", "crawl_session", "content", "url", "length", "status", "level", "type"
    };

    static {
        STATUSES.add("ignored");
        STATUSES.add("ok");
        STATUSES.add("redirection");
        STATUSES.add("request_error");
        STATUSES.add("server_error");
    }

    public static final int SIZE_1K = 1024;
    public static final int SIZE_1M = 1024 * SIZE_1K;

    public static final byte SEPARATOR = '|';
    public static final String SEPARATOR_STRING = new String(new byte[]{SEPARATOR});
    public static final byte[] EMPTY_SHA = DigestUtils.sha256("");
    public static final int MD5_LENGTH = 16;
    public static final int SHA_LENGTH = 32;
    public static final int KEY_LENGTH = 7;
    public static final byte TYPE_MD5 = 1;
    public static final byte TYPE_SHA = 2;
    public static final byte TYPE_SHA_EMPTY = 3;
    public static final int PREFIX_BUFFER = 1000;

//    public static final boolean[][] KEY_PERMUTATIONS;

//    static {
//        List<List<Integer>> ps = MathUtils.bitPermutations(KEY_LENGTH - 1);
//        KEY_PERMUTATIONS = new boolean[ps.size()][KEY_LENGTH];
//        for (int x = 0; x < ps.size(); ++x) {
//            Set<Integer> permutationSet = new HashSet<Integer>(ps.get(x));
//            // don't permute first column (status)
//            KEY_PERMUTATIONS[x][0] = false;
//            for (int y = 1; y < KEY_LENGTH; ++y) {
//                KEY_PERMUTATIONS[x][y] = permutationSet.contains(y - 1);
//            }
//        }
//    }

    public static boolean[][] getKeyCombinations(String permutationType)
    {
        CombinationType p = permutationType == null
            ? CombinationType.normal
            : CombinationType.valueOf(permutationType);

        if (p == CombinationType.explicitDate) {
            return getKeyCombinations(false, false);
        } else if (p == CombinationType.wildcardDate) {
            return getKeyCombinations(false, true);
        } else {
            // 'normal' is the default
            return getKeyCombinations(false);

        }
    }

    /**
     * @param fixed the value for each fixed column
     * @return the key combinations
     */
    protected static boolean[][] getKeyCombinations(final boolean... fixed)
    {
        List<List<Integer>> ps = MathUtils.bitCombinations(KEY_LENGTH - fixed.length);
        boolean[][] p = new boolean[ps.size()][KEY_LENGTH];
        for (int x = 0; x < ps.size(); ++x) {
            Set<Integer> permutationSet = new HashSet<Integer>(ps.get(x));
            // set fixed columns
            System.arraycopy(fixed, 0, p[x], 0, fixed.length);
            // set permuted columns
            for (int y = fixed.length; y < KEY_LENGTH; ++y) {
                p[x][KEY_LENGTH -  y] = permutationSet.contains(y - 1);
            }
        }
        return p;
    }


    public static boolean prefixEquals(ByteBuffer refPrefix, byte[] test)
    {
        // no prefix
        if (refPrefix.position() == 0) return false;

        // test is shorter that reference
        if (test.length < refPrefix.position()) return false;

        for (int i = 0; i < refPrefix.position(); ++i) {
            if (refPrefix.array()[i] != test[i]) return false;
        }
        return true;
    }

    public static boolean equalsToPosition(ByteBuffer b1, ByteBuffer b2)
    {
        if (b1.position() != b2.position()) return false;
        for (int i = 0; i < b1.position(); ++i) {
            if (b1.array()[i] != b2.array()[i]) return false;
        }
        return true;
    }

    public static byte getKeyType(byte[] keyBytes, int keyLength)
    {
        /**
         * MUST check types from LONGEST to SHORTEST suffix, it's
         * the only safe way because bytes '01' '02' and '03' don't
         * happen in the prefix, but may happen is a MD5 or SHA256.
         * Watch for keyLength though!
         */
        if (isContentSha(keyBytes, keyLength)) return TYPE_SHA;
        if (isUrlMd5(keyBytes, keyLength)) return TYPE_MD5;
        if (isEmptySha(keyBytes, keyLength)) return TYPE_SHA_EMPTY;
        try {
            throw new RuntimeException(
                "could not detect type for '" + new String(keyBytes, 0, keyLength, "US-ASCII") + "'"
            );
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean isContentSha(byte[] keyBytes, int keyLength)
    {
        return keyLength > SHA_LENGTH + 2
            && keyBytes[keyLength - SHA_LENGTH - 2] == SEPARATOR
            && keyBytes[keyLength - SHA_LENGTH - 1] == TYPE_SHA;
    }

    private static boolean isUrlMd5(byte[] keyBytes, int keyLength)
    {
        return keyLength > MD5_LENGTH + 2
            && keyBytes[keyLength - MD5_LENGTH - 2] == SEPARATOR
            && keyBytes[keyLength - MD5_LENGTH - 1] == TYPE_MD5;
    }

    private static boolean isEmptySha(byte[] keyBytes, int keyLength)
    {
        return keyLength > 2
            && keyBytes[keyLength - 2] == SEPARATOR
            && keyBytes[keyLength - 1] == TYPE_SHA_EMPTY;
    }

    // partitioner (same permutation+type => same reducer)
    public static class HP extends HashPartitioner<BytesWritable, Long3Writable>
    {
        private byte[] keyBytes;
        private int keyLength;

        @Override
        public int getPartition(BytesWritable key, Long3Writable value, int reducerCount)
        {
            keyBytes = key.getBytes();
            keyLength = key.getLength();

            switch (getKeyType(keyBytes, keyLength)) {
            case TYPE_SHA_EMPTY: // suffix: 2+0 bytes
                return getPartition(reducerCount, keyBytes, keyLength - 2);
            case TYPE_MD5: // suffix: 2+16 bytes
                return getPartition(reducerCount, keyBytes, keyLength - MD5_LENGTH - 2);
            case TYPE_SHA: // suffix : 2+32 bytes
                return getPartition(reducerCount, keyBytes, keyLength - SHA_LENGTH - 2);
            default:
                throw new RuntimeException("could not detect type for '" + new String(keyBytes, 0, keyLength) + "'");
            }
        }

        public int getPartition(int reducers, byte[] keyBytes, int prefixLength)
        {
            return Math.abs(WritableComparator.hashBytes(keyBytes, 0, prefixLength) % reducers);
        }
    }

    // raw grouping comparator
    public static class HGC implements RawComparator<BytesWritable>
    {
        //@Override
        public int compare(byte[] b1, int s1, int l1, byte[] b2, int s2, int l2)
        {
            return group(
                bytesSub(b1, s1 + HKOC.BW_OFFSET, l1 - HKOC.BW_OFFSET), l1 - HKOC.BW_OFFSET,
                bytesSub(b2, s2 + HKOC.BW_OFFSET, l2 - HKOC.BW_OFFSET), l2 - HKOC.BW_OFFSET
            );
        }

        @Override
        public int compare(BytesWritable o1, BytesWritable o2)
        {
            return group(
                o1.getBytes(), o1.getLength(),
                o2.getBytes(), o2.getLength()
            );
        }

        private int group(byte[] b1, int l1, byte[] b2, int l2)
        {
            byte t1 = getKeyType(b1, l1);
            byte t2 = getKeyType(b2, l2);

            int suffixLength1 = t1 == TYPE_SHA ? SHA_LENGTH : t1 == TYPE_MD5 ? MD5_LENGTH : 0;
            int suffixLength2 = t2 == TYPE_SHA ? SHA_LENGTH : t2 == TYPE_MD5 ? MD5_LENGTH : 0;

            return WritableComparator.compareBytes(
                b1, 0, l1 - suffixLength1 - 2,
                b2, 0, l2 - suffixLength2 - 2
            );
        }
    }

    // raw key order comparator
    public static class HKOC implements RawComparator<BytesWritable>
    {
        public static final int BW_OFFSET = 4;

        @Override
        public int compare(byte[] b1, int s1, int l1, byte[] b2, int s2, int l2)
        {
            return sort(
                bytesSub(b1, s1 + BW_OFFSET, l1 - BW_OFFSET), l1 - BW_OFFSET,
                bytesSub(b2, s2 + BW_OFFSET, l2 - BW_OFFSET), l2 - BW_OFFSET
            );
        }

        @Override
        public int compare(BytesWritable o1, BytesWritable o2)
        {
            return sort(
                o1.getBytes(), o1.getLength(),
                o2.getBytes(), o2.getLength()
            );
        }

        private int sort(byte[] b1, int l1, byte[] b2, int l2)
        {
            byte t1 = getKeyType(b1, l1);
            byte t2 = getKeyType(b2, l2);

            int suffixLength1 = t1 == TYPE_SHA ? SHA_LENGTH : t1 == TYPE_MD5 ? MD5_LENGTH : 0;
            int suffixLength2 = t2 == TYPE_SHA ? SHA_LENGTH : t2 == TYPE_MD5 ? MD5_LENGTH : 0;

            // prefix 1 (permutation)
            int c = WritableComparator.compareBytes(
                b1, 0, l1 - suffixLength1 - 2,
                b2, 0, l2 - suffixLength2 - 2
            );
            if (c != 0) return c;

            // prefix 2 (type)
            c = t1 - t2;
            if (c != 0) return c;

            // prefix 3 (md5/sha/empty)
            return WritableComparator.compareBytes(
                b1, l1 - suffixLength1, suffixLength1,
                b2, l2 - suffixLength2, suffixLength2
            );
        }
    }

    public static byte[] bytesSub(byte[] src, int srcStart, int length)
    {
        if (srcStart == 0 && src.length == length) return src;
        byte[] dest = new byte[length];
        System.arraycopy(src, srcStart, dest, 0, length);
        return dest;
    }

    // combiner
    public static class HC extends Reducer<BytesWritable, Long3Writable, BytesWritable, Long3Writable>
    {
        private ByteBuffer currentKey = ByteBuffer.allocate(PREFIX_BUFFER + SHA_LENGTH);
        private BytesWritable key = new BytesWritable();
        private Long3Writable value = new Long3Writable();
        private Long metaSize;
        private Long realSize;
        private long metaCount;

        @Override
        protected void reduce(BytesWritable key, Iterable<Long3Writable> values, Context context)
            throws IOException, InterruptedException
        {
            for (Long3Writable value : values) {

                if (!Bytes.equals(
                    currentKey.array(), 0, currentKey.position(),
                    context.getCurrentKey().getBytes(), 0, context.getCurrentKey().getLength())
                    ) {
                    emitCurrent(context);

                    currentKey.clear();
                    currentKey.put(key.getBytes(), 0, key.getLength());
                    metaSize = value.get()[0];
                    realSize = value.get()[1];
                    metaCount = value.get()[2];
                } else {
                    metaSize += value.get()[0];
                    metaCount += value.get()[2];
                }
            }
        }

        private void emitCurrent(Context context) throws IOException, InterruptedException
        {
            if (currentKey.position() > 0) {
                key.set(currentKey.array(), 0, currentKey.position());
                value.set(metaSize, realSize, metaCount);
                context.write(key, value);
            }
        }

        @Override
        protected void cleanup(Context context) throws IOException, InterruptedException
        {
            emitCurrent(context);
        }
    }

    public static String sizeRange(long size)
    {
        if (size == 0) return "a0";
        else if (size > 120 * SIZE_1M) return "h120M+";
        else if (size > 15 * SIZE_1M) return "g15M-110M";
        else if (size > 1.4 * SIZE_1M) return "f1.4M-15M";
        else if (size > 150 * SIZE_1K) return "e150K-1.4M";
        else if (size > 15 * SIZE_1K) return "d15K-150K";
        else if (size > 1.2 * SIZE_1K) return "c1.2K-15K";
        else return "b0-1.2K";
    }

    public static class Long3Writable extends ArrayPrimitiveWritable
    {
        public Long3Writable()
        {
            super(new long[]{0, 0, 0});
        }

        @Override
        public long[] get()
        {
            return (long[]) super.get();
        }

        @Override
        @Deprecated
        public void set(Object value)
        {
            super.set(value);
        }

        public void set(long metaSize, long realSize, long metaCount)
        {
            get()[0] = metaSize;
            get()[1] = realSize;
            get()[2] = metaCount;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Long3Writable that = (Long3Writable) o;
            return get()[0] == that.get()[0]
                && get()[1] == that.get()[1]
                && get()[2] == that.get()[2];
        }

        @Override
        public int hashCode()
        {
            int result = (int) (get()[0] ^ (get()[0] >>> 32));
            result = 31 * result + (int) (get()[1] ^ (get()[1] >>> 32));
            result = 31 * result + (int) (get()[2] ^ (get()[2] >>> 32));
            return result;
        }

        @Override
        public String toString()
        {
            return "[" + get()[0] + "," + get()[1] + "," + get()[2] + "]";
        }

    }

    @Override
    public int run(String[] args) throws Exception
    {
        ArgsParser parser = ArgsParser.parseArgs(args);
        Job j = new Job(getConf(), getClass().getSimpleName());
        Configuration c = j.getConfiguration();
        c.set(YEAR_KEY, parser.getInteger("year", true) + "");
        c.set(
            COMBINATION_TYPE_KEY,
            CombinationType.valueOf(parser.get("type", CombinationType.normal + ""))+""
        );

        // general
        j.setJarByClass(FacetReduxJob.class);
        int reducers = parser.get("reducers", 1);
        boolean speculativeExecution = false;

        // input
        Path input = new Path(parser.getString("input", true));
        configureInput(j, input);

        // output
        j.setOutputFormatClass(TextOutputFormat.class);
        TextOutputFormat.setOutputPath(
            j,
            new Path(parser.getString("output", true))
        );
        j.getConfiguration().set("mapred.textoutputformat.separator", SEPARATOR_STRING);
        j.getConfiguration().set("mapreduce.textoutputformat.separator", SEPARATOR_STRING);

        // mapper
        j.setMapSpeculativeExecution(speculativeExecution);
        j.setMapperClass(getMapperClass());
        j.setMapOutputKeyClass(BytesWritable.class);
        j.setMapOutputValueClass(Long3Writable.class);

        // combiner
        j.setCombinerClass(HC.class);

        // partitioner / comparator
        j.setPartitionerClass(HP.class);
        j.setSortComparatorClass(HKOC.class);
        j.setGroupingComparatorClass(HGC.class);

        // reducer
        j.setNumReduceTasks(reducers);
        j.setReduceSpeculativeExecution(speculativeExecution);
        j.setReducerClass(FacetReduxReducer.class);
        j.setOutputKeyClass(TextOutputFormat.class);
        j.setOutputValueClass(TextOutputFormat.class);

        // run and log
        parser.usageInfo();
        JobManager manager = new JobManager(j, true);

        //manager.addListener(new JobLogReporter(j, ));
        return manager.runAndMonitor() ? 0 : -1;
    }

    protected abstract Class<? extends FacetReduxMapper> getMapperClass();

    protected abstract void configureInput(Job j, Path input) throws Exception;


    public static void printKeyValue(String name, BytesWritable ck, Long3Writable value)
        throws UnsupportedEncodingException
    {
        byte[] kb = ck.getBytes();
        int kl = ck.getLength();
        byte type = getKeyType(kb, kl);
        String p = null, s = null;
        if (type == TYPE_SHA_EMPTY) {
            p = new String(kb, 0, kl - 2, "US-ASCII");
            s = "";
        }
        if (type == TYPE_SHA) {
            p = new String(kb, 0, kl - SHA_LENGTH - 2, "US-ASCII");
            s = Hex.encodeHexString(Bytes.tail(Bytes.head(kb, kl), SHA_LENGTH));
        }
        if (type == TYPE_MD5) {
            p = new String(kb, 0, kl - MD5_LENGTH - 2, "US-ASCII");
            s = Hex.encodeHexString(Bytes.tail(Bytes.head(kb, kl), MD5_LENGTH));
        }
        //if (!"*|*|*|*|*|*|*".equals(p)) return;
        System.out.println(name + p + "[" + type + "]" + s + " = " + value);
    }

}
