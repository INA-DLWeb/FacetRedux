package fr.ina.dlweb.proprioception.utils;

import org.apache.commons.beanutils.BeanUtils;

import java.io.Serializable;
import java.util.Comparator;

/**
 * Date: 06/05/14
 * Time: 14:01
 *
 * @author drapin
 */
public class BeanComparator<T> implements Comparator<T>, Serializable
{
    private final String[] propertyNames;

    public BeanComparator(String... propertyNames)
    {
        this.propertyNames = propertyNames;
    }

    @SuppressWarnings("unchecked")
    @Override
    public int compare(T o1, T o2)
    {
        for (String n : propertyNames) {
            try {
                Comparable c1 = BeanUtils.getProperty(o1, n);
                Comparable c2 = BeanUtils.getProperty(o2, n);
                if (c1 == c2) continue;
                if (c1 == null) return -1;
                if (c2 == null) return 1;
                int c = c1.compareTo(c2);
                if (c == 0) continue;
                return c;
            } catch (Exception e) {
                 throw new RuntimeException(e);
            }
        }
        return 0;
    }
}
