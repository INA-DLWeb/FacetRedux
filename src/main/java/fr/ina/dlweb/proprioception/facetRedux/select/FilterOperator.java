package fr.ina.dlweb.proprioception.facetRedux.select;

/**
 * Date: 16/04/14
 * Time: 16:36
 *
 * @author drapin
 */
public enum FilterOperator
{
    lower_than("<", true, false),
    lower_than_equal("<=", true, false),
    equal("=", false, false),
    like("LIKE", false, false),
    in("IN", false, true),
    different("!=", false, false),
    greater_than(">", true, false),
    greater_than_equal(">=", true, false)
    ;
    public final String symbol;
    public final boolean numerical;
    public final boolean listOperator;

    private FilterOperator(String symbol, boolean numerical, boolean listOperator)
    {
        this.symbol = symbol;
        this.numerical = numerical;
        this.listOperator = listOperator;
    }

    public boolean isListOperator()
    {
        return listOperator;
    }

    public String getSymbol()
    {
        return symbol;
    }
}
