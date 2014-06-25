package fr.ina.dlweb.proprioception.facetRedux.job;

import com.google.common.primitives.Bytes;
import fr.ina.dlweb.daff.DAFFConstants;
import fr.ina.dlweb.hadoop.HadoopClientCDH4;
import fr.ina.dlweb.hadoop.HadoopTestUtils;
import fr.ina.dlweb.hadoop.io.StreamableDAFFRecordWritable;
import fr.ina.dlweb.hadoop.io.StreamableDAFFRecordWriter;
import fr.ina.dlweb.utils.StringUtils;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mrunit.mapreduce.MapDriver;
import org.apache.hadoop.mrunit.mapreduce.MapReduceDriver;
import org.apache.hadoop.mrunit.types.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.*;

import static org.testng.Assert.assertEquals;

/**
 * Date: 12/12/13
 * Time: 17:34
 *
 * @author drapin
 */
public class FacetReduxTest
{
    private static final Logger log = LoggerFactory.getLogger(FacetReduxTest.class);

    public static final String URL_1 = "http://www.francetv.org/index.html";
    public static final String BODY_1 = "<html>coucou</html>"; // 19 bytes
    public static final Map<String, String> META_1 = new HashMap<String, String>();

    public static final String URL_2 = "http://facebook.com/blip.png";
    public static final String BODY_2 = ""; // 0 bytes
    public static final String BODY_2b = "imageDataLOL:)"; // 14 bytes
    public static final Map<String, String> META_2 = new HashMap<String, String>();
    public static final Map<String, String> META_2b = new HashMap<String, String>();

    private static final Object[][] ENTRIES;

    static {
        // "date", "crawl_session", "url", "status", "level", "type", "content", "length"
        META_1.put("date", "2013-08-01T13:59:00Z");
        META_1.put("crawl_session", "france2.fr@20130801T123910Z");
        META_1.put("url", URL_1);
        META_1.put("status", "ok");
        META_1.put("level", "1");
        META_1.put("type", "text/html");

        META_2.put("date", "2013-08-02T14:49:01Z");
        META_2.put("crawl_session", "tf1.fr@20130802T132904Z");
        META_2.put("url", URL_2);
        META_2.put("status", "ok");
        META_2.put("level", "1");
        META_2.put("type", "image/png");

        META_2b.put("date", "2013-08-10T06:22:22Z");
        META_2b.put("crawl_session", "tf1.fr@20130810T061111Z");
        META_2b.put("url", URL_2);
        META_2b.put("status", "ok");
        META_2b.put("level", "1");
        META_2b.put("type", "image/png");

        ENTRIES = new Object[][]{
            // month, status, site, contentCategory, tld, sizeCategory, level
            new Object[]{URL_1, BODY_1,  new String[]{"ok", "2013-08", "france2.fr", "HTML", "org", FacetReduxJob.sizeRange(BODY_1.length()), "1"}},
            new Object[]{URL_2, BODY_2,  new String[]{"ok", "2013-08", "tf1.fr", "IMAGE", "com", FacetReduxJob.sizeRange(BODY_2.length()), "1"}},
            new Object[]{URL_2, BODY_2b, new String[]{"ok", "2013-08", "tf1.fr", "IMAGE", "com", FacetReduxJob.sizeRange(BODY_2b.length()), "1"}}
        };
    }

    //@Test
    public void testRewindClear()
    {
        ByteBuffer b = ByteBuffer.allocate(100);
        assertEquals(b.position(), 0);

        b.clear();
        assertEquals(b.position(), 0);

        b.put(new byte[]{'a', 'b', 'c'});
        assertEquals(b.position(), 3);
        assertEquals(b.get(0), 'a');
        assertEquals(b.get(1), 'b');
        assertEquals(b.get(2), 'c');

        b.clear();
        assertEquals(b.position(), 0);

        b.put(new byte[]{1, 'a', 3}); // 3 bytes
        b.putLong(12); // 8 bytes
        b.putLong(1); // 8 bytes
        assertEquals(b.position(), 19);

        assertEquals(b.get(0), 1);
        assertEquals(b.get(1), 'a');

        byte[] dest = new byte[b.position()];
        assertEquals(dest.length, b.position());

        b.rewind();
        b.get(dest, 0, dest.length);
        assertEquals(// Arrays.equals(
            dest,
            new byte[]{1, 'a', 3, 0, 0, 0, 0, 0, 0, 0, 12, 0, 0, 0, 0, 0, 0, 0, 1}
        );
    }

    @Test
    public void showPermutations()
    {
        final boolean[][] p = FacetReduxJob.getKeyPermutation(false);
        int c = 0;
        for (int x = 0; x < p.length; ++x) {
            for (int y = 0; y < p[x].length; ++y) {
                System.out.print(String.format("%-5s ", p[x][y]));
            }
            c++;
            System.out.print("\n");
        }
        System.out.println(">>" + c);
    }

    @Test
    public void testMapper() throws IOException, InterruptedException
    {
        FacetReduxJobDAFF.HM2 mapper = new FacetReduxJobDAFF.HM2();
        MapDriver<BytesWritable, StreamableDAFFRecordWritable, BytesWritable, FacetReduxJob.Long3Writable>
            mDriver = MapDriver.newMapDriver(mapper);

        mDriver.withInput(new BytesWritable(), HadoopTestUtils.getMetaRecordWritable(META_1, BODY_1));
        mDriver.withInput(new BytesWritable(), HadoopTestUtils.getMetaRecordWritable(META_2, BODY_2));
        mDriver.withInput(new BytesWritable(), HadoopTestUtils.getMetaRecordWritable(META_2b, BODY_2b));

        List<Pair<BytesWritable, FacetReduxJob.Long3Writable>>
            generatedOutput = getGeneratedMapperOutput(ENTRIES);

        log.info("(generated) output keys : {}", generatedOutput.size());
        log.info("(generated) output keys per input : {}", generatedOutput.size() / ENTRIES.length);

        Set<String> distinctKeys = new HashSet<String>();
        int bytes = 0;
        for (Pair<BytesWritable, FacetReduxJob.Long3Writable> b : generatedOutput) {
            distinctKeys.add(new String(b.getFirst().getBytes()));
            bytes += b.getFirst().getLength();
            mDriver.withOutput(b.getFirst(), b.getSecond());
        }

        log.info("distinct output keys : {}", distinctKeys.size());
        log.info("distinct output keys per input : {}", distinctKeys.size() / ENTRIES.length);
        log.info("total key bytes : {}", bytes);

        mDriver.runTest(false);
    }

    public void testOrder() throws DecoderException, UnsupportedEncodingException
    {
        /*
         M>*|*|*|*|*|*|*[1]e0fb49fdc9fe32d8d56092dabd495092 = [19,19,1]
         M>*|*|*|*|*|*|*[2]12a20b98973adb430d2ad8fca4619f01dec664bfc996d4bd6a84169db311b6df = [19,19,1]
         M>*|*|*|*|*|*|*[1]e4ce4ef96e7c1fc76bd158341e613cfd = [0,0,1]
         M>*|*|*|*|*|*|*[3] = [0,0,1]
         M>*|*|*|*|*|*|*[1]e4ce4ef96e7c1fc76bd158341e613cfd = [14,14,1]
         M>*|*|*|*|*|*|*[2]89655e8031959afdba456f9cc22fcec3a1473c51de9bd179f9b0e5a14e05fc4e = [14,14,1]
        */

        List<BytesWritable> keys = new ArrayList<BytesWritable>();
        String[] permutations = new String[]{
            "*|*|*|*|*|*|*",
            "*|ok|*|*|*|*|*",
            "2013-08|*|*|*|*|*|*",
        };

        for (String p : permutations) {
            keys.addAll(Arrays.asList(
                getKey(p, FacetReduxJob.TYPE_MD5, "e0fb49fdc9fe32d8d56092dabd495092"),
                getKey(p, FacetReduxJob.TYPE_SHA, "12a20b98973adb430d2ad8fca4619f01dec664bfc996d4bd6a84169db311b6df"),
                getKey(p, FacetReduxJob.TYPE_MD5, "e4ce4ef96e7c1fc76bd158341e613cfd"),
                getKey(p, FacetReduxJob.TYPE_SHA_EMPTY, ""),
                getKey(p, FacetReduxJob.TYPE_MD5, "e4ce4ef96e7c1fc76bd158341e613cfd"),
                getKey(p, FacetReduxJob.TYPE_SHA, "89655e8031959afdba456f9cc22fcec3a1473c51de9bd179f9b0e5a14e05fc4e")
            ));
        }

        Collections.shuffle(keys);

        for (BytesWritable k : keys) {
            FacetReduxJob.printKeyValue("SORT1> ", k, null);
        }

        System.out.println("--");
        Collections.sort(keys, new FacetReduxJob.HKOC());

        for (BytesWritable k : keys) {
            FacetReduxJob.printKeyValue("SORT2> ", k, null);
        }
    }

    private BytesWritable getKey(String prefix, byte type, String hexSuffix)
        throws DecoderException, UnsupportedEncodingException
    {
        return new BytesWritable(Bytes.concat(
            prefix.getBytes("US-ASCII"),
            new byte[]{'|', type},
            Hex.decodeHex(hexSuffix.toCharArray())
        ));
    }

    @Test
    public void testMR() throws IOException
    {
        FacetReduxJobDAFF.HM2 mapper = new FacetReduxJobDAFF.HM2();
        FacetReduxReducer reducer = new FacetReduxReducer();
        MapReduceDriver
            <BytesWritable, StreamableDAFFRecordWritable, BytesWritable, FacetReduxJob.Long3Writable, Text, Text>
            driver = MapReduceDriver.newMapReduceDriver(mapper, reducer);

        driver.setKeyOrderComparator(new FacetReduxJob.HKOC());
        driver.setKeyGroupingComparator(new FacetReduxJob.HGC());

        FacetReduxJob.HC combiner = new FacetReduxJob.HC();
        driver.setCombiner(combiner);

        driver.withInput(new BytesWritable(), HadoopTestUtils.getMetaRecordWritable(META_1, BODY_1));
        driver.withInput(new BytesWritable(), HadoopTestUtils.getMetaRecordWritable(META_2, BODY_2));
        driver.withInput(new BytesWritable(), HadoopTestUtils.getMetaRecordWritable(META_2b, BODY_2b));

        List<Pair<Text, Text>> output = driver.run();
        int bytes = 0;
        for (Pair<Text, Text> p : output) {
            bytes += p.getFirst().getLength() + p.getSecond().getLength();
            log.info("RESULT: " + p.getFirst().toString() + " -- " + p.getSecond().toString());
        }
        log.info("results:" + output.size());
        log.info("total bytes : " + bytes);
    }

    public void createMockDaffRecord() throws IOException, InterruptedException
    {
        HadoopClientCDH4 client = FacetReduxJobDAFF.getHadoopClient();
        FSDataOutputStream target = client.getFS().create(new Path("/user/public/david_test.daff"), true);
        StreamableDAFFRecordWriter w = new StreamableDAFFRecordWriter(target, DAFFConstants.DAFF_CURRENT_VERSION);
        w.write(new BytesWritable(), HadoopTestUtils.getMetaRecordWritable(META_1, BODY_1));
        w.write(new BytesWritable(), HadoopTestUtils.getMetaRecordWritable(META_2, BODY_2));
        w.write(new BytesWritable(), HadoopTestUtils.getMetaRecordWritable(META_2b, BODY_2b));
        target.close();
    }

    private List<Pair<BytesWritable, FacetReduxJob.Long3Writable>> getGeneratedMapperOutput(Object[][] entries)
        throws UnsupportedEncodingException
    {
        List<Pair<BytesWritable, FacetReduxJob.Long3Writable>> generatedOutput =
            new ArrayList<Pair<BytesWritable, FacetReduxJob.Long3Writable>>();

        for (Object[] oEntry : entries) {

            String url = (String) oEntry[0];
            String body = (String) oEntry[1];
            String[] entry = (String[]) oEntry[2];
            long size = body.length();
            FacetReduxJob.Long3Writable value = new FacetReduxJob.Long3Writable();
            value.set(size, size, 1);

            for (boolean[] p : FacetReduxJob.getKeyPermutations(FacetReduxJob.PermutationType.normal+"")) {

                List<String> entryTuple = new ArrayList<String>();
                for (int i = 0; i < p.length; ++i) entryTuple.add(p[i] ? "*" : entry[i]);

                // sha key
                byte[] keySha;
                if ("".equals(body)) {
                    keySha = Bytes.concat(
                        StringUtils.join(entryTuple, "|").getBytes("US-ASCII"),
                        new byte[]{FacetReduxJob.TYPE_SHA_EMPTY}
                    );
                } else {
                    keySha = Bytes.concat(
                        StringUtils.join(entryTuple, "|").getBytes("US-ASCII"),
                        new byte[]{FacetReduxJob.TYPE_SHA},
                        DigestUtils.sha256(body)
                    );
                }

                generatedOutput.add(new Pair<BytesWritable, FacetReduxJob.Long3Writable>(
                    new BytesWritable(keySha), value
                ));

                // md5 key
                byte[] keyMd5 = Bytes.concat(
                    StringUtils.join(entryTuple, "|").getBytes("US-ASCII"),
                    new byte[]{FacetReduxJob.TYPE_MD5},
                    DigestUtils.md5(url)
                );

                generatedOutput.add(new Pair<BytesWritable, FacetReduxJob.Long3Writable>(
                    new BytesWritable(keyMd5), value
                ));
            }
        }
        return generatedOutput;
    }
}
