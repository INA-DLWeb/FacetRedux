package fr.ina.dlweb.proprioception.utils;

import fr.ina.dlweb.mapreduce.MetadataAvroSchema;
import fr.ina.dlweb.mapreduce.job.JobLogReporter;
import fr.ina.dlweb.mapreduce.job.JobManager;
import fr.ina.dlweb.utils.ArgsParser;
import fr.ina.dlweb.utils.Transform;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.mapred.AvroKey;
import org.apache.avro.mapreduce.AvroJob;
import org.apache.avro.mapreduce.AvroKeyInputFormat;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;
import org.apache.hadoop.util.Tool;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Date: 22/01/14
 * Time: 11:08
 *
 * @author drapin
 */
public abstract class MetadataAvroShaSampler extends Configured implements Tool
{
    public static final String NAME = MetadataAvroShaSampler.class.getSimpleName();
    public static final String TRANSFORM_CLASS_KEY = "fr.ina.dlweb.transformClass";

    public abstract Class<? extends Transform<Map<CharSequence, CharSequence>, String>> getTransformClass();

    public static class ExtractMapper
        extends Mapper<AvroKey<GenericRecord>, NullWritable, Text, Text>
    {
        private Transform<Map<CharSequence, CharSequence>, String> transform;
        private final Text outKeyText = new Text();
        private final Text outValueSha = new Text();

        @Override
        protected void setup(Context context) throws IOException, InterruptedException
        {
            //recordNumber = 0;
            String transformClassName = context.getConfiguration().get(TRANSFORM_CLASS_KEY);
            try {
                transform = (Transform) Class.forName(transformClassName).newInstance();
            } catch (Exception e) {
                throw new RuntimeException("'" + transformClassName + "' instantiation problem", e);
            }
        }

        @Override
        protected void map(AvroKey<GenericRecord> key, NullWritable value, Context context)
            throws IOException, InterruptedException
        {
            Map<CharSequence, CharSequence> metadata = (Map<CharSequence, CharSequence>) key.datum().get("content");
            String outValueString = transform.apply(metadata);
            if (outValueString != null) {
                outKeyText.set(outValueString);
                outValueSha.set((String) metadata.get("content"));
                context.write(outKeyText, outValueSha);
                context.getCounter(NAME, "metadata.transformed").increment(1);
            } else {
                context.getCounter(NAME, "metadata.nullIgnored").increment(1);
            }
        }
    }

    public static final class FirstKeyReducer extends Reducer<Text, Text, Text, Text>
    {
        private final Text valueOut = new Text();

        @Override
        protected void reduce(Text key, Iterable<Text> values, Context context)
            throws IOException, InterruptedException
        {
            String sha = null;
            for (Text v : values) if (sha == null) sha = v.toString();
            valueOut.set(sha);
            context.write(key, valueOut);
        }
    }

    private Job createJob(List<String> inputs, String output, final Configuration conf, int reducers) throws Exception
    {
        Job job = new Job(conf, getClass().getSimpleName());
        Configuration jobConf = job.getConfiguration();

        // add current JAR to dependencies
        job.setJarByClass(getClass());
        job.setJarByClass(MetadataAvroShaSampler.class);

        // transform class
        jobConf.set(TRANSFORM_CLASS_KEY, getTransformClass().getName());

        // map
        job.setMapperClass(ExtractMapper.class);
        // map input class
        job.setInputFormatClass(AvroKeyInputFormat.class);
        Schema avroSchema = new Schema.Parser().parse(MetadataAvroSchema.AVRO_SCHEMA);
        AvroJob.setInputKeySchema(job, avroSchema);
        // map output class
        job.setMapOutputKeyClass(Text.class);
        job.setMapOutputValueClass(Text.class);

        // reducer
        job.setNumReduceTasks(reducers);
        job.setReducerClass(FirstKeyReducer.class);
        job.setOutputFormatClass(TextOutputFormat.class);

        // input files
        for (String in : inputs) {
            FileInputFormat.addInputPath(job, new Path(in));
        }

        // output files
        FileOutputFormat.setOutputPath(job, new Path(output));
        FileOutputFormat.setCompressOutput(job, false);
        //FileOutputFormat.setOutputCompressorClass(job, MetadataAvroFilter.getCompressor(jobConf));

        return job;
    }

    public static void printUsage()
    {
        System.err.println("Usage: " + NAME +
            "-inputs=<comma separated list of absolute metadata dir> " +
            "-output=<absolute dir of output>");
    }

    @Override
    public int run(String[] args) throws Exception
    {
        ArgsParser params = ArgsParser.parseArgs(args);

        System.out.println(params);

        String inputs = params.get("inputs");
        String output = params.get("output");

        if (inputs == null || output == null) {
            printUsage();
            System.exit(1);
        }

        final Configuration conf = getConf();
        Job job = createJob(
            Arrays.asList(inputs.split(",")),
            output,
            conf,
            params.get("reducers", 7)
        );

        // logging
        JobManager jobManager = new JobManager(job, true);
        Path logFile = new Path(new Path(output).toString() + ".log");
        Writer logWriter = new OutputStreamWriter(logFile.getFileSystem(conf).create(logFile, true));
        jobManager.addListener(new JobLogReporter(job, logWriter));
        int status = jobManager.runAndMonitor() ? 0 : 1;
        logWriter.close();

        return status;
    }

}

