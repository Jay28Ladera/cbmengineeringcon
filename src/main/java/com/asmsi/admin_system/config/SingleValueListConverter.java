package com.asmsi.admin_system.config;

import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.ConditionalGenericConverter;

import java.util.*;

/**
 * Spring turns a request parameter that arrives ONCE into a List by splitting it on commas, so a form with one
 * line whose description is "Duct tape, silver 2 in" became two descriptions and the line columns no longer
 * matched. For List&lt;String&gt; / Collection&lt;String&gt; targets this keeps the single value whole.
 * Lists of numbers are left to Spring's own converter.
 */
public class SingleValueListConverter implements ConditionalGenericConverter {
    @Override
    public Set<ConvertiblePair> getConvertibleTypes() {
        return Set.of(new ConvertiblePair(String.class, Collection.class));
    }

    @Override
    public boolean matches(TypeDescriptor source, TypeDescriptor target) {
        TypeDescriptor el = target.getElementTypeDescriptor();
        return el != null && el.getType() == String.class;
    }

    @Override
    public Object convert(Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {
        if (source == null) return null;
        Collection<String> out = targetType.getType().isAssignableFrom(ArrayList.class) ? new ArrayList<>() : new LinkedHashSet<>();
        out.add((String) source);
        return out;
    }
}
