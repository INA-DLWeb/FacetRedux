package fr.ina.dlweb.proprioception.facetRedux.select;

import fr.ina.dlweb.utils.JsonUtils;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

/**
 * Date: 17/04/14
 * Time: 11:57
 *
 * @author drapin
 */
@Test
public class FilterTest
{
    public void json()
    {
        // serialization
        Filter filter = new Filter(
            FilterType.status,
            FilterOperator.equal,
            "ok"
        );
        String filterJson = JsonUtils.toJson(filter);
        assertEquals(
            filterJson,
            "{\"type\":\"status\",\"operator\":\"equal\",\"value\":[\"ok\"]}"
        );

        // parsing
        Filter parsedFilter = JsonUtils.parseJson(filterJson, Filter.class);
        assertEquals(parsedFilter, filter);

        // serialize again
        String filterJson2 = JsonUtils.toJson(parsedFilter);
        assertEquals(filterJson2, filterJson);
    }

    public void sqlIn()
    {
        Filter filter = new Filter(
            FilterType.status,
            FilterOperator.in,
            "ok", "redirection"
        );
        assertEquals(filter.getSQL(), "status IN ('ok','redirection')");
    }

    public void sqlGTE()
    {
        Filter filter = new Filter(
            FilterType.level,
            FilterOperator.greater_than_equal,
            "12"
        );
        assertEquals(filter.getSQL(), "level >= 12");
    }
}
