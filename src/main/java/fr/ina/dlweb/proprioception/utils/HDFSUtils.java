package fr.ina.dlweb.proprioception.utils;

import fr.ina.dlweb.utils.CollectionUtils;
import fr.ina.dlweb.utils.IOUtils;
import fr.ina.dlweb.utils.Predicate;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Iterator;

/**
 * Date: 25/02/14
 * Time: 14:12
 *
 * @author drapin
 */
public class HDFSUtils
{
    public static FileStatus[] getResults(final FileSystem fs, final Path path) throws IOException
    {
        return CollectionUtils.grep(fs.globStatus(new Path(path, "*")), new Predicate<FileStatus>()
        {
            @Override
            public boolean evaluate(FileStatus f)
            {
                return f.isFile()
                    && f.getPath().getName().startsWith("part-r-");
            }
        });
    }

    public static long countResultLines(final FileSystem fs, final Path path) throws IOException
    {
        long lines = 0;
        for (String line : getResultLines(fs, path)) ++lines;
        return lines;
    }

    public static Iterable<String> getResultLines(final FileSystem fs, final Path path) throws IOException
    {
        return new ROIterable<String>()
        {
            private final FileStatus[] results = getResults(fs, path);
            private int currentResult = 0;
            private FSDataInputStream resultIS = null;
            private BufferedReader resultReader = null;
            private String currentLine = null;

            @Override
            protected String loadNext() throws Exception
            {
                // all result files have been read
                if (currentResult >= results.length) return null;

                // result file reader is not initialized
                if (resultIS == null || resultReader == null) {
                    resultIS = fs.open(results[currentResult].getPath());
                    resultReader = new BufferedReader(new InputStreamReader(resultIS));
                }

                // read next line in current result file
                currentLine = resultReader.readLine();

                // EOF, shift to next result file
                if (currentLine == null) {
                    IOUtils.closeQuietly(resultIS);
                    IOUtils.closeQuietly(resultReader);
                    resultIS = null;
                    resultReader = null;
                    currentResult++;
                    return loadNext();
                }

                return currentLine;
            }
        };
    }

    public abstract static class DefaultIterable<T> implements Iterable<T>
    {
        private static final Logger log = LoggerFactory.getLogger(HDFSUtils.DefaultIterable.class);
        private T item = null;
        private boolean shouldShift = true;

        @Override
        public Iterator<T> iterator()
        {
            init();
            return new Iterator<T>()
            {
                @Override
                public boolean hasNext()
                {
                    shiftIfNeeded();
                    return item != null;
                }

                @Override
                public T next()
                {
                    shiftIfNeeded();
                    shouldShift = true;
                    return item;
                }

                @Override
                public void remove()
                {
                    DefaultIterable.this.remove();
                }
            };
        }

        protected abstract void remove();

        protected void init() { }

        protected void onError(Throwable t)
        {
            log.error(t == null ? "null exception" : t.getMessage());
            throw new RuntimeException(t);
        }

        private void shiftIfNeeded()
        {
            if (!shouldShift) return;
            try {
                item = loadNext();
                shouldShift = false;
            } catch (Throwable t) {
                onError(t);
                item = null;
            }
        }

        protected abstract T loadNext() throws Exception;
    }

    public abstract static class ROIterable<T> extends DefaultIterable<T>
    {
        @Override
        protected void remove()
        {
            throw new IllegalStateException("this is a read-only iterator.");
        }
    }
}
