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
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.value.EncodedValue;
import com.android.tools.smali.dexlib2.iface.value.IntEncodedValue;
import com.android.tools.smali.dexlib2.iface.value.NullEncodedValue;
import com.android.tools.smali.dexlib2.iface.value.StringEncodedValue;

public class InnerClassRecord {
    public final String name;
    public final int accessFlags;

    public InnerClassRecord(String name, int accessFlags) {
        this.name = name;
        this.accessFlags = accessFlags;
    }

    public static InnerClassRecord findIn(ClassDef classDef) {
        for (Annotation annotation : classDef.getAnnotations()) {
            if (!TypeUtils.TYPE_DALVIK_INNER_CLASS.equals(annotation.getType())) continue;
            return findIn(annotation);
        }
        return null;
    }

    public static InnerClassRecord findIn(Annotation annotation) {
        String name = null;
        int accessFlags = -1;
        for (AnnotationElement element : annotation.getElements()) {
            if ("name".equals(element.getName())) {
                EncodedValue value = element.getValue();
                if (value instanceof NullEncodedValue) return null;
                name = ((StringEncodedValue) value).getValue();
            } else if ("accessFlags".equals(element.getName())) {
                accessFlags = ((IntEncodedValue) element.getValue()).getValue();
            }
        }
        if (name != null && accessFlags != -1) {
            return new InnerClassRecord(name, accessFlags);
        }
        return null;
    }
}
