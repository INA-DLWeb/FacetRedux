package fr.ina.dlweb.proprioception.utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;

/**
 * Date: 24/01/14
 * Time: 13:48
 *
 * @author drapin
 * todo: move to dlweb commons
 */
public class SubkeyCsvLocalReducer extends CsvLocalReducer
{
    private final int[] subkeyFieldIndexes;

    public SubkeyCsvLocalReducer(
        InputStream sortedCsv,
        String outputFilePath,
        boolean overwriteResult,
        int[] subkeyFieldIndexes)
    {
        super(sortedCsv, outputFilePath, overwriteResult);
        this.subkeyFieldIndexes = subkeyFieldIndexes;
    }

    private String[] current = null;

    @Override
    protected void reducer(String[] data, Writer result) throws IOException
    {
        //if (data.length != subkeyFieldCount) return;
        data = normalizeData(data);
        if (data == null) return;

        // first iteration
        if (current == null) {
            current = data;
            reduceValue = initReduceValue(current);
            return;
        }

        // detect if subkey changed
        boolean newKey = false;
        for (int subkeyFieldIndex : subkeyFieldIndexes) {
            if (!data[subkeyFieldIndex].equals(current[subkeyFieldIndex])) {
                newKey = true;
            }
        }

        if (newKey) {
            writeResult(result, reduceValue);
            current = data;
            reduceValue = initReduceValue(current);
        } else {
            reduceValue = reduce(reduceValue, current);
        }
    }

    protected String[] normalizeData(String[] data)
    {
        return data;
    }

    protected void writeResult(Writer result, long reduceValue) throws IOException
    {
        for (int subkeyFielIndex : subkeyFieldIndexes) {
            result.write(current[subkeyFielIndex]);
            result.write(getOutFieldSeparator());
        }
        result.write(reduceValue + "\n");
    }

    protected long reduceValue = 0;

    protected long reduce(long reduceValue, String[] current) {
        return 1 + reduceValue;
    }
    protected long initReduceValue(String[] current) {
        return 1;
    }
}
