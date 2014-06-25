package fr.ina.dlweb.proprioception.utils;

import fr.ina.dlweb.utils.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Arrays;

/**
 * Date: 24/01/14
 * Time: 12:37
 *
 * @author drapin
 * todo: move to dlweb commons
 */
public abstract class CsvLocalReducer
{
    private static final Logger log = LoggerFactory.getLogger(CsvLocalReducer.class);

    private final InputStream sortedCsv;
    private final File outputFile;
    private String inputFieldSeparatorRegex = ",";
    private String outFieldSeparator = ",";

    public CsvLocalReducer(InputStream sortedCsvFilePath, String outputFilePath, boolean overwriteResult)
    {
        sortedCsv = sortedCsvFilePath;
//        if (!sortedCsvFile.exists()) {
//            throw new IllegalArgumentException("input file does not exist: '" + sortedCsvFile.getAbsolutePath() + "'");
//        }

        outputFile = new File(outputFilePath);
        if (!overwriteResult && outputFile.exists()) {
            throw new IllegalArgumentException("output file already exists: '" + outputFile.getAbsolutePath() + "'");
        }
    }

    public final String getInputFieldSeparatorRegex()
    {
        return inputFieldSeparatorRegex;
    }

    public void setInputFieldSeparatorRegex(String inputFieldSeparatorRegex)
    {
        this.inputFieldSeparatorRegex = inputFieldSeparatorRegex;
    }

    public void run() throws IOException
    {
        BufferedReader r = new BufferedReader(new InputStreamReader(sortedCsv));
        FileWriter w = new FileWriter(outputFile);
        String line;
        while ((line = r.readLine()) != null) {
            String[] data = line.split(getInputFieldSeparatorRegex());
            try {
                reducer(data, w);
            } catch (Exception e) {
                log.error("could not reduce line : " + Arrays.asList(data) + " (" + e.getMessage() + ")");
            }
        }
        IOUtils.closeQuietly(w);
        IOUtils.closeQuietly(r);
    }

    public String getOutFieldSeparator()
    {
        return outFieldSeparator;
    }

    public void setOutFieldSeparator(String outFieldSeparator)
    {
        this.outFieldSeparator = outFieldSeparator;
    }

    protected abstract void reducer(String[] data, Writer result) throws IOException;
}
