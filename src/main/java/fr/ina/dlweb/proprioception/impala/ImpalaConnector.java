package fr.ina.dlweb.proprioception.impala;

import com.fasterxml.jackson.databind.util.LRUMap;
import fr.ina.dlweb.utils.DateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;

/**
 * Date: 03/01/14
 * Time: 16:14
 *
 * @author drapin
 */
public class ImpalaConnector
{
    private static final Logger log = LoggerFactory.getLogger(ImpalaConnector.class);

    private final String impalaServer;
    private final LRUMap<String, List<String>> cache;
    private final ThreadLocal<Connection> connection;

    public ImpalaConnector(String impalaServer)
    {
        this.impalaServer = impalaServer;
        cache = new LRUMap<String, List<String>>(1, 50);

        connection = new ThreadLocal<Connection>()
        {
            @Override
            protected Connection initialValue()
            {
                return createConnection();
            }
        };
    }

    private Connection createConnection()
    {
        try {
            Class.forName(org.apache.hive.jdbc.HiveDriver.class.getName());
            Properties props = new Properties();
            return DriverManager.getConnection(
                "jdbc:hive2://" + impalaServer + ":21050/;auth=noSasl",
                props
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

//    public Map<String, Long> countDistinct(String columnName, String where) throws SQLException
//    {
//        where = (where == null) ? "" : " where " + where;
//
//        // count values
//        ResultSet r = query("select count(distinct " + columnName + ") from TABLE " + where);
//        r.next();
//        long distinctValues = r.getLong(1);
//        r.close();
//
//        // list distinct values
//        r = query("select " + columnName + ", " +
//            "count(*) as c from TABLE " + where +
//            " group by tld  order by c " +
//            "limit " + distinctValues
//        );
//        Map<String, Long> result = new HashMap<String, Long>();
//        while (r.next()) {
//            result.put(
//                r.getString(1),
//                r.getLong(2)
//            );
//        }
//        r.close();
//        return result;
//    }

    public final ResultSet query(String sql) throws SQLException
    {
        return query(sql, false);
    }

    protected synchronized ResultSet query(String sql, boolean isRetry) throws SQLException
    {
        try {
            Statement s = connection.get().createStatement();
            log.info("SQL query : {}", sql);
            return s.executeQuery(sql);
        } catch (OutOfMemoryError e) {
            if (isRetry) throw e;
            log.warn("retrying failed query ({})", e.getMessage());
            connection.get().close();
            connection.set(createConnection());
            return query(sql, true);
        }
    }

    public void update(String sql) throws SQLException
    {
        Statement s = connection.get().createStatement();
        log.info("SQL update : {}", sql);
        s.executeUpdate(sql);
    }

    public List<String> csv(String sql) throws SQLException
    {
        List<String> results = cache.get(sql);
        if (results != null) {
            log.info("CSV lines (cached) : {}", results.size());
            return results;
        }

        long start = System.currentTimeMillis();
        results = new ArrayList<String>();
        ResultSet r = query(sql);
        int cols = r.getMetaData().getColumnCount();
        while (r.next()) {
            StringBuilder b = new StringBuilder();
            for (int i = 1; i <= cols; ++i) {
                b.append(r.getString(i));
                if (i != cols) b.append(",");
            }
            results.add(b.toString());
        }
        r.close();
        long duration = (System.currentTimeMillis() - start) / 1000;


        if (results.size() < 5000) {
            log.info("CSV lines (caching) : {} (duration: {})", results.size(), DateUtil.formatDuration(duration));
            cache.put(sql, results);
        } else {
            log.info("CSV lines (not caching) : {}", results.size());
        }
        return results;
    }

    public void dropIfExists(String tableName) throws SQLException
    {
        String cleanTable = "DROP TABLE IF EXISTS " + tableName;
        update(cleanTable);
    }
}
