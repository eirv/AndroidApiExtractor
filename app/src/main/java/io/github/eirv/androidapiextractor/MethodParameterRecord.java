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

import java.util.Set;

public class MethodParameterRecord {
    public final int id;
    public final String type;
    public final Set<? extends Annotation> annotations;
    public String name;
    public String signature;

    int registerId;

    public MethodParameterRecord(
            int id,
            String name,
            String type,
            String signature,
            Set<? extends Annotation> annotations) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.signature = signature;
        this.annotations = annotations;
    }
}
