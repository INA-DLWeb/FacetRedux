package fr.ina.dlweb.proprioception.facetRedux.job;

import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Reducer;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Date: 10/02/14
 * Time: 15:16
 *
 * @author drapin
 */ // reducer
public class FacetReduxReducer extends Reducer<BytesWritable, FacetReduxJob.Long3Writable, Text, Text>
{
    private Text outKey = new Text();
    private Text outValue = new Text();
    private final ByteBuffer currentPrefix = ByteBuffer.allocate(FacetReduxJob.PREFIX_BUFFER);
    private final ByteBuffer currentSuffix = ByteBuffer.allocate(FacetReduxJob.SHA_LENGTH + 1);
    private Byte currentType = null;
    private long currentMetaCount = 0;
    private long currentUrlCount = 0;
    private long currentShaCount = 0;
    private long currentMetaSize = 0;
    private long currentRealSize = 0;

    BytesWritable inKey;
    //Long3Writable inValue;
    byte[] keyBytes;
    long[] aValue;
    int keyLength;
    private final ByteBuffer prefix = ByteBuffer.allocate(FacetReduxJob.PREFIX_BUFFER);
    private final ByteBuffer suffix = ByteBuffer.allocate(FacetReduxJob.SHA_LENGTH + 1);
    private byte type;
    private boolean increase;

    @Override
    protected void setup(Context context) throws IOException, InterruptedException
    {
        super.setup(context);
        reset();
    }

    private void reset()
    {
        currentPrefix.clear();
        currentSuffix.clear();
        prefix.clear();
        suffix.clear();
        increase = false;
        currentType = 0; //null;

        currentMetaCount = 0;
        currentUrlCount = 0;
        currentShaCount = 0;
        currentMetaSize = 0;
        currentRealSize = 0;
    }

    @Override
    protected void cleanup(Context context) throws IOException, InterruptedException
    {
        super.cleanup(context);
        emitCurrentValue(context);
        reset();
    }

    @Override
    protected void reduce(BytesWritable key, Iterable<FacetReduxJob.Long3Writable> values, Context context)
        throws IOException, InterruptedException
    {
        // System.out.println("-r-");
        for (FacetReduxJob.Long3Writable inValue : values) {
            //inValue = context.getCurrentValue();
            aValue = inValue.get();

            inKey = key; //context.getCurrentKey();
            keyBytes = inKey.getBytes();
            keyLength = inKey.getLength();

            // printKeyValue("R>", inKey, inValue);

            // read TYPE and SUFFIX
            suffix.clear();
            type = FacetReduxJobAvro.getKeyType(keyBytes, keyLength);
            if (type == FacetReduxJobAvro.TYPE_SHA) {
                suffix.put(keyBytes, keyLength - FacetReduxJob.SHA_LENGTH, FacetReduxJob.SHA_LENGTH);
            } else if (type == FacetReduxJobAvro.TYPE_MD5) {
                suffix.put(keyBytes, keyLength - FacetReduxJob.MD5_LENGTH, FacetReduxJob.MD5_LENGTH);
            }

            if (FacetReduxJob.prefixEquals(currentPrefix, keyBytes)) {
                // same prefix

                if (currentType == null) throw new RuntimeException("null type");
                increase = false;

                if (type != currentType) {
                    // new type
                    increase = true;
                    currentType = type;

                } else if (!FacetReduxJob.equalsToPosition(currentSuffix, suffix)) {
                    // new suffix
                    increase = true;
                    currentSuffix.clear();
                    currentSuffix.put(suffix.array(), 0, suffix.position());
                } // else { same type AND same suffix }

                // new type OR new suffix
                if (increase) {
                    // increase counters
                    if (currentType == FacetReduxJob.TYPE_MD5) {
                        currentUrlCount++;
                    } else if (currentType == FacetReduxJob.TYPE_SHA_EMPTY) {
                        currentShaCount++;
                        //currentRealSize += aValue[1];
                    } else if (currentType == FacetReduxJob.TYPE_SHA) {
                        currentShaCount++;
                        currentRealSize += aValue[1];
                    }
                }

                // each meta emits 2 types : { urlMD5, contentSha } or { urlMD5, contentEmpty }
                // to count meta records, we count each urlMD5 type.
                if (type == FacetReduxJob.TYPE_MD5) {
                    currentMetaCount += aValue[2];
                    currentMetaSize += aValue[0];
                }

            } else {
                // new prefix

                // emit for current prefix (is not null)
                emitCurrentValue(context);

                // init. current prefix
                currentPrefix.clear();
                if (type == FacetReduxJob.TYPE_MD5)
                    currentPrefix.put(keyBytes, 0, keyLength - FacetReduxJob.MD5_LENGTH - 2);
                else if (type == FacetReduxJob.TYPE_SHA)
                    currentPrefix.put(keyBytes, 0, keyLength - FacetReduxJob.SHA_LENGTH - 2);
                else if (type == FacetReduxJob.TYPE_SHA_EMPTY)
                    currentPrefix.put(keyBytes, 0, keyLength - 2);

                // init current type
                currentType = type;

                // init. current suffix
                currentSuffix.clear();
                currentSuffix.put(suffix.array(), 0, suffix.position());

                // init. counters (including this record)
                if (type == FacetReduxJob.TYPE_MD5) {
                    currentUrlCount = 1;
                    currentShaCount = 0;
                    currentRealSize = 0;
                } else {
                    currentUrlCount = 0;
                    currentShaCount = 1;
                    currentRealSize = aValue[1];
                }
                currentMetaCount = aValue[2];
                currentMetaSize = aValue[0];

            }
        }
    }

    private void emitCurrentValue(Context context) throws IOException, InterruptedException
    {
        if (currentPrefix.position() > 0) {
            outKey.set(currentPrefix.array(), 0, currentPrefix.position());
            outValue.set(currentMetaCount + FacetReduxJob.SEPARATOR_STRING +
                currentUrlCount + FacetReduxJob.SEPARATOR_STRING +
                currentShaCount + FacetReduxJob.SEPARATOR_STRING +
                currentMetaSize + FacetReduxJob.SEPARATOR_STRING +
                currentRealSize
            );
            context.write(outKey, outValue);
        }
    }
}
