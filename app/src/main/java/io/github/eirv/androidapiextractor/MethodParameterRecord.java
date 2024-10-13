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
