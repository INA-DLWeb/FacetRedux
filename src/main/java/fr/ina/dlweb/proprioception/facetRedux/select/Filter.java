package fr.ina.dlweb.proprioception.facetRedux.select;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang.StringUtils;

import java.util.Arrays;

/**
 * Date: 16/04/14
 * Time: 16:35
 *
 * @author drapin
 */
public class Filter
{
    public final FilterType type;
    public final FilterOperator operator;
    public final String[] value;

    @JsonCreator
    public Filter(
        @JsonProperty("type") FilterType type,
        @JsonProperty("operator") FilterOperator operator,
        @JsonProperty("value") String... value)
    {
        this.type = type;
        this.operator = operator;
        this.value = value;
        if (value != null && value.length > 1 && operator != FilterOperator.in) {
            throw new IllegalArgumentException("arrays only work with 'in' operator : " + Arrays.asList(value));
        }
    }

    @JsonIgnore
    public String getSQL()
    {
        // quote
        String q = operator.numerical ? "" : "'";
        // serialized value
        String v = (operator == FilterOperator.in)
            ? "(" + q + StringUtils.join(value, q + "," + q) + q + ")"
            : q + value[0] + q;
        // filter
        return type.name() + " " + operator.symbol + " " + v;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Filter f = (Filter) o;
        return operator == f.operator && type == f.type && Arrays.equals(value, f.value);
    }

    @Override
    public int hashCode()
    {
        int result = type.hashCode();
        result = 31 * result + operator.hashCode();
        result = 31 * result + Arrays.hashCode(value);
        return result;
    }
}
