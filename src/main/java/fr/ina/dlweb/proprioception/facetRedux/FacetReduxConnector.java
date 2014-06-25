package fr.ina.dlweb.proprioception.facetRedux;

import fr.ina.dlweb.proprioception.facetRedux.select.Filter;
import fr.ina.dlweb.proprioception.facetRedux.select.FilterOperator;
import fr.ina.dlweb.proprioception.facetRedux.select.FilterType;
import fr.ina.dlweb.proprioception.facetRedux.select.Indicator;
import fr.ina.dlweb.proprioception.impala.ImpalaConnector;
import fr.ina.dlweb.utils.CollectionUtils;
import fr.ina.dlweb.utils.CountMap;
import fr.ina.dlweb.utils.Predicate;
import org.apache.commons.lang.StringUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * Date: 10/03/14
 * Time: 11:02
 *
 * @author drapin
 */
public class FacetReduxConnector extends ImpalaConnector
{
    private final String SCHEMA = "facetRedux";
    private final String defaultTable;

    public FacetReduxConnector(String impalaServer)
    {
        this(impalaServer, null);
    }

    public FacetReduxConnector(String impalaServer, String defaultTable)
    {
        super(impalaServer);
        this.defaultTable = defaultTable;
    }

    @Override
    protected synchronized ResultSet query(String sql, boolean isRetry) throws SQLException
    {
        if (defaultTable != null) {
            sql = sql.replaceAll("TABLE", defaultTable);
        }
        return super.query(sql, isRetry);
    }

    public void importYear(String source, int year) throws SQLException
    {
        // import into regular table
        String tableName = SCHEMA + ".facets_" + year;
        dropIfExists(tableName);
        update(getImportTableSQL(source, tableName));

        // move to 'parquet' table
        String tableNameParquet = tableName + "p";
        dropIfExists(tableNameParquet);
        update("CREATE TABLE " + tableNameParquet + " LIKE " + tableName + " STORED AS PARQUET");
        update("INSERT OVERWRITE TABLE " + tableNameParquet + " SELECT * FROM " + tableName);
    }

    public static String getImportTableSQL(String source, String tableName)
    {
        StringBuilder sql = new StringBuilder("CREATE EXTERNAL TABLE ").append(tableName).append("(\n");
        for (FilterType f : FilterType.values()) sql.append(' ').append(f).append(" STRING,\n");
        for (int i = 0; i < Indicator.values().length; ++i) {
            sql.append(' ').append(Indicator.values()[i]).append(" BIGINT");
            if (i + 1 != Indicator.values().length) sql.append(',');
            sql.append('\n');
        }
        sql.append(")\n");
        sql.append("ROW FORMAT DELIMITED FIELDS TERMINATED BY '|'\n");
        sql.append("LOCATION '").append(source).append("'");
        return sql.toString();
    }

    public List<String> getYearTables() throws SQLException
    {
        return CollectionUtils.grep(csv("show tables in " + SCHEMA), new Predicate<String>()
        {
            @Override
            public boolean evaluate(String tableName)
            {
                return tableName.startsWith("facets_") && tableName.endsWith("p");
            }
        });
    }

    public CountMap<Indicator> getStats(String table) throws SQLException
    {
        return getStats(table, null);
    }

    public CountMap<Indicator> getStats(String table, final Set<Filter> filters) throws SQLException
    {
        // fix table canonical name
        if (!table.startsWith(SCHEMA)) { table = SCHEMA + "." + table; }

        // create filters map
        Map<FilterType, Filter> filterMap = new HashMap<FilterType, Filter>();
        if (filters != null) {
            for (Filter f : filters) filterMap.put(f.type, f);
        }

        // fix filter value for status
        if (!filterMap.containsKey(FilterType.status)) {
            filterMap.put(FilterType.status, new Filter(
                FilterType.status, FilterOperator.equal, "ok"
            ));
        }

        StringBuilder sql = new StringBuilder("SELECT ");
        sql.append(StringUtils.join(Indicator.values(), ", "));
        sql.append(" FROM ").append(table).append(" WHERE ");
        for (int i = 0; i < FilterType.values().length; ++i) {
            FilterType ft = FilterType.values()[i];
            if (filterMap.containsKey(ft)) {
                Filter f = filterMap.get(ft);
                sql.append(f.getSQL());
            }
            else {
                sql.append(ft).append("='*'");
            }
            if (i + 1 != FilterType.values().length) sql.append(" AND ");
        }
        ResultSet r = query(sql.toString());
        CountMap<Indicator> stats = new CountMap<Indicator>();
        if (!r.next()) {
            throw new RuntimeException("no data in table '" + table + "'");
        }
        for (Indicator i : Indicator.values()) {
            stats.inc(i, r.getLong(i.name()));
        }
        r.close();
        return stats;
    }

}
