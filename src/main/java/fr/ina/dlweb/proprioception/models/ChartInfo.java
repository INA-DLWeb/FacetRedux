package fr.ina.dlweb.proprioception.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Arrays;

/**
 * Date: 24/05/13
 * Time: 15:23
 *
 * @author drapin
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChartInfo
{
    private final String xType;
    private final String sort;
    private final int x;
    private final int y;
    private final int key;
    private final String type;
    private final String transform;

    @JsonCreator()
    @JsonIgnoreProperties(ignoreUnknown = true)
    public ChartInfo(
        @JsonProperty("x-type") String xType,
        @JsonProperty("sort-column") String sort,
        @JsonProperty("x") int x,
        @JsonProperty("y") int y,
        @JsonProperty("key") int key,
        @JsonProperty("type") String type,
        @JsonProperty("transform") String transform
    )
    {
        if (xType == null) xType = "int";
        if (!Arrays.asList("int", "date", "string").contains(xType)) xType = "int";

        if (sort == null) sort = "";
        if (!Arrays.asList("x", "y", "key").contains(sort)) sort = "";

        if (transform == null) transform = "";

        this.xType = xType;
        this.sort = sort;
        this.x = x;
        this.y = y;
        this.key = key;
        this.type = type;
        this.transform = transform;
    }

    // int, date or string
    public String getxType()
    {
        return xType;
    }

    public int getX()
    {
        return x;
    }

    public int getY()
    {
        return y;
    }

    public String getSort()
    {
        return sort;
    }

    public int getKey()
    {
        return key;
    }

    public String getType()
    {
        return type == null ? "" : type;
    }

    public String getTransform()
    {
        return transform == null ? "" : transform;
    }

    @JsonIgnore
    public String getTransformEncoded()
    {
        try {
            return URLEncoder.encode(getTransform(), "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException e) {

            System.err.println("error while URI-encoding 'transform' function");
            e.printStackTrace();

            return "";
        }
    }

    public static ChartInfo create(String xType, String sort, String x, String y, String key, String type, String transform)
    {
        int xCol = -1;
        try { xCol = Integer.parseInt(x); } catch (NumberFormatException e) { /* ignore */ }

        int keyCol = -1;
        try { keyCol = Integer.parseInt(key); } catch (NumberFormatException e) { /* ignore */ }

        int yCol = -1;
        try { yCol = Integer.parseInt(y); } catch (NumberFormatException e) { /* ignore */ }

        return type == null || type.isEmpty() ? null : new ChartInfo(xType, sort, xCol, yCol, keyCol, type, transform);
    }

}
