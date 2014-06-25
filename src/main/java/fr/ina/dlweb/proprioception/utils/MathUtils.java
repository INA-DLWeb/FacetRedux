package fr.ina.dlweb.proprioception.utils;

import java.io.IOException;
import java.util.*;

/**
 * Date: 05/05/14
 * Time: 17:07
 *
 * @author drapin
 */
public class MathUtils
{
    public static List<Map.Entry<String, List<Double>>> boxPlot(
        List<String> csvLines, final Integer keyIndex, final int valueIndex, final int quantileCount
    ) throws IOException
    {
        Map<String, List<Double>> values = new HashMap<String, List<Double>>();

        for (String line : csvLines) {
            String[] data = line.split(",");
            if (keyIndex != null && keyIndex >= data.length) continue;
            if (valueIndex >= data.length) continue;
            String key = keyIndex == null ? "" : data[keyIndex];
            List<Double> v = values.get(key);
            if (v == null) {
                v = new ArrayList<Double>();
                values.put(key, v);
            }
            Double d = Double.parseDouble(data[valueIndex]);
            if (d.isNaN()) {
                System.err.println(line);
                continue;
            }
            v.add(d);
        }

        List<Map.Entry<String, List<Double>>> boxes = new ArrayList<Map.Entry<String, List<Double>>>();
        for (Map.Entry<String, List<Double>> e : values.entrySet()) {
            List<Double> quantiles = boxPlot(e.getValue(), quantileCount);
            boxes.add(new AbstractMap.SimpleEntry<String, List<Double>>(
                e.getKey(),
                quantiles
            ));
        }

        // order quantiles by key
        Collections.sort(boxes, new BeanComparator<Map.Entry<String, ?>>("key"));

        return boxes;
    }

    public static List<Double> boxPlot(List<Double> values, int quantiles) throws IOException
    {
        Collections.sort(values);
        double step = 1d * values.size() / (1d * quantiles);
        List<Double> quantileList = new ArrayList<Double>(quantiles);
        for (int i = 0; i <= quantiles; i++) {
            Double q = values.get(Math.min(
                (int) (step * i),
                values.size() - 1
            ));
            quantileList.add(q);
        }
        return quantileList;
    }

}
