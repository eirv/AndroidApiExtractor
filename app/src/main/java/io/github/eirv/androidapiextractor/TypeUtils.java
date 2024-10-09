/*
 * This file is part of AndroidApiExtractor.
 *
 * AndroidApiExtractor is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * AndroidApiExtractor is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with AndroidApiExtractor.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.eirv.androidapiextractor;

import com.android.tools.smali.dexlib2.iface.Annotation;
import com.android.tools.smali.dexlib2.iface.AnnotationElement;
import com.android.tools.smali.dexlib2.iface.reference.MethodReference;
import com.android.tools.smali.dexlib2.iface.value.ArrayEncodedValue;
import com.android.tools.smali.dexlib2.iface.value.EncodedValue;
import com.android.tools.smali.dexlib2.iface.value.StringEncodedValue;
import com.android.tools.smali.dexlib2.iface.value.TypeEncodedValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class TypeUtils {
    public static final String TYPE_DALVIK_ENCLOSING_CLASS = "Ldalvik/annotation/EnclosingClass;";
    public static final String TYPE_DALVIK_INNER_CLASS = "Ldalvik/annotation/InnerClass;";
    public static final String TYPE_DALVIK_MEMBER_CLASSES = "Ldalvik/annotation/MemberClasses;";
    public static final String TYPE_DALVIK_ENCLOSING_METHOD = "Ldalvik/annotation/EnclosingMethod;";
    public static final String TYPE_DALVIK_SIGNATURE = "Ldalvik/annotation/Signature;";
    public static final String TYPE_DALVIK_METHOD_PARAMETERS =
            "Ldalvik/annotation/MethodParameters;";
    public static final String TYPE_DALVIK_THROWS = "Ldalvik/annotation/Throws;";
    public static final String TYPE_DALVIK_ANNOTATION_DEFAULT =
            "Ldalvik/annotation/AnnotationDefault;";
    public static final String TYPE_DALVIK_PERMITTED_SUBCLASSES =
            "Ldalvik/annotation/PermittedSubclasses;";
    public static final String TYPE_DALVIK_RECORD = "Ldalvik/annotation/Record;";

    public static final String TYPE_OBJECT = "Ljava/lang/Object;";
    public static final String TYPE_STRING = "Ljava/lang/String;";
    public static final String TYPE_THROWABLE = "Ljava/lang/Throwable;";
    public static final String TYPE_ANNOTATION = "Ljava/lang/Annotation;";

    private static final String TEST_PREFIX = "__renamed/";

    public static String toCfName(String descriptor) {
        if (descriptor == null) return null;
        String cfName = descriptor.substring(1, descriptor.length() - 1);
        if (AndroidApiExtractor.MAKE_TEST_JAR_FOR_JVM) {
            return TYPE_OBJECT.equals(descriptor)
                            || TYPE_STRING.equals(descriptor)
                            || TYPE_THROWABLE.equals(descriptor)
                            || TYPE_ANNOTATION.equals(descriptor)
                    ? cfName
                    : TEST_PREFIX + cfName;
        }
        return cfName;
    }

    public static String[] toCfName(List<String> descriptors) {
        String[] result = descriptors.toArray(new String[0]);
        for (int i = 0; result.length > i; i++) {
            result[i] = toCfName(result[i]);
        }
        return result;
    }

    public static String getOuterName(String name) {
        int index = name.lastIndexOf('/');
        return name.substring(0, name.indexOf('$', index));
    }

    public static String getMethodDescriptor(MethodReference method) {
        StringBuilder sb = new StringBuilder();
        sb.append('(');
        for (CharSequence param : method.getParameterTypes()) {
            sb.append(wrapType(String.valueOf(param)));
        }
        sb.append(')');
        sb.append(wrapType(method.getReturnType()));
        return sb.toString();
    }

    public static String getComponentType(String type) {
        if (type == null) return null;
        char c = type.charAt(0);
        if (c == 'L') {
            return type;
        } else if (c == '[') {
            int index = type.indexOf('L');
            if (index == -1) return type;
            return type.substring(index);
        }
        return type;
    }

    public static String wrapType(String type) {
        if (!AndroidApiExtractor.MAKE_TEST_JAR_FOR_JVM) return type;
        if (type == null) return null;
        if (type.contains(TYPE_OBJECT)
                || type.contains(TYPE_STRING)
                || type.contains(TYPE_THROWABLE)
                || type.contains(TYPE_ANNOTATION)) {
            return type;
        }
        char c = type.charAt(0);
        if (c == 'L') {
            return "L" + TEST_PREFIX + type.substring(1);
        } else if (c == '[') {
            int index = type.indexOf('L');
            if (index == -1) return type;
            return type.substring(0, index + 1) + TEST_PREFIX + type.substring(index + 1);
        }
        return type;
    }

    public static String getSignature(Set<? extends Annotation> annotations) {
        for (Annotation annotation : annotations) {
            if (TypeUtils.TYPE_DALVIK_SIGNATURE.equals(annotation.getType())) {
                List<? extends EncodedValue> value =
                        ((ArrayEncodedValue) assertAndGetSingleValue(annotation)).getValue();
                StringBuilder sb = new StringBuilder();
                for (EncodedValue childValue : value) {
                    sb.append(wrapType(((StringEncodedValue) childValue).getValue()));
                }
                return sb.toString();
            }
        }
        return null;
    }

    public static String[] getExceptions(Set<? extends Annotation> annotations) {
        for (Annotation annotation : annotations) {
            if (TypeUtils.TYPE_DALVIK_THROWS.equals(annotation.getType())) {
                List<? extends EncodedValue> value =
                        ((ArrayEncodedValue) assertAndGetSingleValue(annotation)).getValue();
                List<String> result = new ArrayList<>();
                for (EncodedValue childValue : value) {
                    result.add(TypeUtils.toCfName(((TypeEncodedValue) childValue).getValue()));
                }
                return result.toArray(new String[0]);
            }
        }
        return null;
    }

    public static EncodedValue assertAndGetSingleValue(Annotation annotation) {
        Set<? extends AnnotationElement> elements = annotation.getElements();
        if (elements.size() != 1) {
            throw new AssertionError(elements.size());
        }
        AnnotationElement element = elements.iterator().next();
        if (!"value".equals(element.getName())) {
            throw new AssertionError(element.getName());
        }
        return element.getValue();
    }
}
