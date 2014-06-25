package fr.ina.dlweb.proprioception.facetRedux.job;

import fr.ina.dlweb.pig.extension.CrawlSession2Fiche;
import fr.ina.dlweb.pig.extension.ExtractMonth;
import fr.ina.dlweb.pig.extension.URL2TLD;
import fr.ina.dlweb.utils.ContentCategory;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.mapreduce.Mapper;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Map;

/**
 * Date: 13/01/14
 * Time: 18:37
 *
 * @author drapin
 */ // mapper
public abstract class FacetReduxMapper<MAPKEY, MAPVAL> extends Mapper<MAPKEY, MAPVAL, BytesWritable,
    FacetReduxJob.Long3Writable>
{
    public static final String COUNTER_PREFIX = "proprioception";
    protected final ByteBuffer keyBytes = ByteBuffer.allocate(FacetReduxJob.PREFIX_BUFFER);
    protected final BytesWritable key = new BytesWritable();
    protected final FacetReduxJob.Long3Writable value = new FacetReduxJob.Long3Writable();
    private String yearFilter;
    private String yearFilterName;
    private boolean[][] keyCombinations;

    /**
     * these variables are kept at instance level to avoid
     * local variables allocation costs at each call to map().
     */
    private Map<String, String> metadata;
    private String url;
    private String month;
    private String status;
    private String site;
    //private String topDomain;
    private String tld;
    private String contentCategory;
    private String sizeCategory;
    private String level;
    private String length;
    private byte[] contentSha;
    private byte[] urlMd5;
    private long contentSize;


    @Override
    protected void setup(final Context context) throws IOException, InterruptedException
    {
        super.setup(context);
        keyCombinations = FacetReduxJob.getKeyCombinations(
            context.getConfiguration().get(FacetReduxJob.COMBINATION_TYPE_KEY)
        );
        yearFilter = context.getConfiguration().get(FacetReduxJob.YEAR_KEY);
        yearFilterName = "not_year:" + yearFilter;
        keyBytes.clear();
    }

    protected abstract Map<String, String> getMetadataFields(MAPKEY inputKey, MAPVAL inputValue, Context context)
        throws IOException;

    @Override
    protected void map(MAPKEY inputKey, MAPVAL inputValue, Context context)
        throws IOException, InterruptedException
    {
        metadata = getMetadataFields(inputKey, inputValue, context);
        if (metadata == null) return;

        // parsed the JSON for this metadata with errors, ignore
        if (metadata.containsKey("parse_error")) {
            context.getCounter(COUNTER_PREFIX, "meta_parse_error").increment(1);
            return;
        }

        // month
        month = ExtractMonth.getMonth(metadata.get("date"));
        if (month == null) {
            context.getCounter(COUNTER_PREFIX, "no_date").increment(1);
            return;
        }
        if (yearFilter != null && !month.startsWith(yearFilter)) {
            context.getCounter(COUNTER_PREFIX, yearFilterName).increment(1);
            return;
        }

        // url
        url = metadata.get("url");
        if (url == null || url.isEmpty()) {
            context.getCounter(COUNTER_PREFIX, "no_url").increment(1);
            return;
        }

        // url MD5
        urlMd5 = DigestUtils.md5(url);
        if (urlMd5.length != FacetReduxJob.MD5_LENGTH) {
            context.getCounter(COUNTER_PREFIX, "bad_url_md5_length").increment(1);
            return;
        }

        // content sha
        try {
            contentSha = Hex.decodeHex(metadata.get("content").toCharArray());
        } catch (Exception e) {
            context.getCounter(COUNTER_PREFIX, "bad_sha").increment(1);
            System.err.println("bad_sha:" + metadata.get("content"));
            return;
        }
        if (contentSha.length != FacetReduxJob.SHA_LENGTH) {
            context.getCounter(COUNTER_PREFIX, "bad_sha_length").increment(1);
            return;
        }

        // content size
        length = metadata.get("length");
        try {
            contentSize =
                (length == null || length.isEmpty() || "null".equals(length) || Bytes.equals(FacetReduxJob.EMPTY_SHA,
                    contentSha))
                    ? 0
                    : Long.parseLong(length);
        } catch (NumberFormatException e) {
            context.getCounter(COUNTER_PREFIX, "bad_length").increment(1);
            //e.printStackTrace();
            return;
        }

        // content size range
        sizeCategory = FacetReduxJob.sizeRange(contentSize);

        // status (or null)
        status = metadata.get("status");
        if (status == null) status = "null";
        if (!FacetReduxJob.STATUSES.contains(status)) {
            context.getCounter(COUNTER_PREFIX, "bad_status").increment(1);
            return;
        }

        // site name (or null)
        site = CrawlSession2Fiche.getSite(metadata.get("crawl_session")) + "";

        // content category (or null)
        contentCategory = ContentCategory.resolve(url, metadata.get("type")) + "";

        // URL TLD
        //topDomain = URL2TopDomain.getTopDomain(url);
        //tld = URL2TLD.getFromHost(topDomain);
        tld = URL2TLD.getFromUrl(url);
        if (tld == null) tld = "null";

        // crawl level
        level = metadata.get("level");
        if (level == null) level = "null";

        emitCombinations(
            context, urlMd5, contentSha, contentSize,
            status, month,
            site, contentCategory, tld, sizeCategory, level
        );
    }

    @SuppressWarnings("ForLoopReplaceableByForEach")
    public void emitCombinations(Context context, byte[] urlMd5, byte[] contentSha, long size, String... keyPrefix)
        throws IOException, InterruptedException
    {
        if (keyPrefix == null || keyPrefix.length != FacetReduxJob.KEY_LENGTH) return;
        context.getCounter(COUNTER_PREFIX, "used_meta").increment(1);
        value.set(size, size, 1);

        // for each permutation
        for (int x = 0; x < keyCombinations.length; ++x) {

            // generate key prefix
            keyBytes.clear();
            // for each item in the permutation
            for (int y = 0; y < FacetReduxJob.KEY_LENGTH; ++y) {
                if (keyCombinations[x][y]) {
                    keyBytes.put((byte) '*').put(FacetReduxJob.SEPARATOR);
                } else {
                    keyBytes.put(keyPrefix[y].getBytes(FacetReduxJob.US_ASCII)).put(FacetReduxJob.SEPARATOR);
                }
            }

            keyBytes.mark();

            // 1: emit url md5
            keyBytes.put(FacetReduxJob.TYPE_MD5).put(urlMd5);
            key.set(keyBytes.array(), 0, keyBytes.position());
            context.write(key, value);

            keyBytes.reset();

            // 2: emit content sha
            if (Arrays.equals(contentSha, FacetReduxJob.EMPTY_SHA)) {
                keyBytes.put(FacetReduxJob.TYPE_SHA_EMPTY);
            } else {
                keyBytes.put(FacetReduxJob.TYPE_SHA).put(contentSha);
            }
            key.set(keyBytes.array(), 0, keyBytes.position());
            context.write(key, value);
        }
    }
}
