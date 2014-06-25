package fr.ina.dlweb.proprioception.facetRedux.job;

import fr.ina.dlweb.proprioception.facetRedux.JobLauncher;
import fr.ina.dlweb.utils.ArgsParser;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.mapred.AvroKey;
import org.apache.avro.mapreduce.AvroJob;
import org.apache.avro.mapreduce.AvroKeyInputFormat;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Date: 09/01/14
 * Time: 17:33
 *
 * @author drapin
 */
public class FacetReduxJobAvro extends FacetReduxJob
{
    public static final String AVRO_SCHEMA = "{" +
        "\"namespace\": \"fr.ina.dlweb.dowser.avromodel.record\", " +
        "\"type\": \"record\", " +
        "\"name\": \"Metadata\", " +
        "\"fields\": [{" +
        "  \"name\": \"content\", " +
        "  \"type\": {" +
        "    \"type\": \"map\", " +
        "    \"name\": \"MetadataContent\", " +
        "    \"values\": \"string\"" +
        "  }" +
        "}]" +
        "}";

    @Override
    protected void configureInput(Job j, Path input) throws IOException
    {
        j.setInputFormatClass(AvroKeyInputFormat.class);
        Schema avroSchema = new Schema.Parser().parse(AVRO_SCHEMA);
        AvroJob.setInputKeySchema(j, avroSchema);
        FileInputFormat.addInputPath(j, input);
    }

    @Override
    protected Class<? extends FacetReduxMapper> getMapperClass()
    {
        return HM1.class;
    }

    public static final class HM1 extends FacetReduxMapper<AvroKey<GenericRecord>, NullWritable>
    {
        private final Map<String, String> meta = new HashMap<String, String>();
        private Map/*<CharSequence, CharSequence>*/ map;

        public HM1() { }

        @Override
        protected Map<String, String> getMetadataFields(
            AvroKey<GenericRecord> inputKey,
            NullWritable inputValue,
            Context context
        ) throws IOException
        {
            meta.clear();
            map = (Map/*<CharSequence, CharSequence>*/) inputKey.datum().get("content");
            for (String key : META_KEYS) {
                meta.put(key, map.get(key) + "");
            }
            return meta;
        }
    }

    // 328.71MB : 211BE548-73F1-11E3-BC6A-CC857A842EC4-m-00151.avro
    //  77.63MB : C84A67DE-6273-11E3-BD32-8737F4AF75FA-m-00152.avro

    public static void main(String[] args) throws Exception
    {
        ArgsParser parser = ArgsParser.parseArgs(args);
        String year = parser.get("year", "2013");
        String reducers = parser.get("reducers", "200");
        parser.usageInfo();

        JobLauncher j = new JobLauncher(
            FacetReduxJobAvro.class,
            //"-input=/user/public/avro-metadata/data-deflate/F872B9B2-253C-11E3-9746-C70361A2F2D2-m-02370.avro", // ~500mb
            //"-input=/user/public/avro-metadata/new-data-deflate/C84A67DE-6273-11E3-BD32-8737F4AF75FA-m-00152.avro", // 77mb
            //"-input=/user/public/facetRedux/errorFiltered/1392127753360/C84A67DE-6273-11E3-BD32-8737F4AF75FA-m-00000.avro", // 18mb
            "-input=/user/public/avro-metadata/*/*.avro",
            "-output=/user/public/facetRedux/" + year + "/" + System.currentTimeMillis() + "",
            "-reducers=" + reducers,
            "-year=" + year
        );
        j.run();
    }
}
