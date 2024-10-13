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

import static org.objectweb.asm.Opcodes.*;

import android.os.Build;
import android.os.Environment;

import com.android.tools.smali.dexlib2.AnnotationVisibility;
import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.HiddenApiRestriction;
import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.ValueType;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedAnnotation;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedClassDef;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedField;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedMethod;
import com.android.tools.smali.dexlib2.iface.Annotation;
import com.android.tools.smali.dexlib2.iface.AnnotationElement;
import com.android.tools.smali.dexlib2.iface.BasicAnnotation;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.MethodImplementation;
import com.android.tools.smali.dexlib2.iface.MethodParameter;
import com.android.tools.smali.dexlib2.iface.MultiDexContainer;
import com.android.tools.smali.dexlib2.iface.debug.DebugItem;
import com.android.tools.smali.dexlib2.iface.debug.StartLocal;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.iface.reference.FieldReference;
import com.android.tools.smali.dexlib2.iface.reference.MethodReference;
import com.android.tools.smali.dexlib2.iface.value.AnnotationEncodedValue;
import com.android.tools.smali.dexlib2.iface.value.ArrayEncodedValue;
import com.android.tools.smali.dexlib2.iface.value.BooleanEncodedValue;
import com.android.tools.smali.dexlib2.iface.value.ByteEncodedValue;
import com.android.tools.smali.dexlib2.iface.value.CharEncodedValue;
import com.android.tools.smali.dexlib2.iface.value.DoubleEncodedValue;
import com.android.tools.smali.dexlib2.iface.value.EncodedValue;
import com.android.tools.smali.dexlib2.iface.value.EnumEncodedValue;
import com.android.tools.smali.dexlib2.iface.value.FieldEncodedValue;
import com.android.tools.smali.dexlib2.iface.value.FloatEncodedValue;
import com.android.tools.smali.dexlib2.iface.value.IntEncodedValue;
import com.android.tools.smali.dexlib2.iface.value.LongEncodedValue;
import com.android.tools.smali.dexlib2.iface.value.MethodEncodedValue;
import com.android.tools.smali.dexlib2.iface.value.ShortEncodedValue;
import com.android.tools.smali.dexlib2.iface.value.StringEncodedValue;
import com.android.tools.smali.dexlib2.iface.value.TypeEncodedValue;

import com.google.common.io.ByteStreams;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InnerClassNode;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class AndroidApiExtractor {
    static final boolean MAKE_TEST_JAR_FOR_JVM = false;

    private static final boolean MAKE_STUB_METHOD = true;
    private static final boolean TRANSFORM_HIDDEN_API_RESTRICTION = true;
    private static final boolean APPEND_RESOURCE_FILES = true;
    private static final boolean APPEND_RESOURCE_BLOCKS = true;

    private static final String TYPE_HIDDEN_API_RESTRICTION =
            "Landroid/annotation/HiddenApiRestriction;";
    private static final String FIELD_HIDDEN_API_RESTRICTION_VALUE = "value";

    private final Opcodes dexOpcodes = Opcodes.forApi(Build.VERSION.SDK_INT);

    private final TreeMap<String, byte[]> classes = new TreeMap<>();
    private final TreeMap<String, byte[]> inaccessibleClasses = new TreeMap<>();
    private boolean hasHiddenApiRestrictions;

    public static void main(String[] args) throws IOException {
        String bootClassPath = System.getenv("BOOTCLASSPATH");
        if (bootClassPath == null) return;
        AndroidApiExtractor extractor = new AndroidApiExtractor();
        List<String> jars = Arrays.asList(bootClassPath.split(":"));
        Collections.reverse(jars);
        extractor.extract(jars);
        String name =
                "android-" + Build.VERSION.SDK_INT + (MAKE_TEST_JAR_FOR_JVM ? "-test.jar" : ".jar");
        File androidJar = new File(Environment.getExternalStorageDirectory(), name);
        //noinspection IOStreamConstructor
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(androidJar))) {
            extractor.writeTo(out);
        }
        System.out.println("Done!");
    }

    private static Object toAsmValue(EncodedValue encodedValue) {
        if (encodedValue == null) return null;
        switch (encodedValue.getValueType()) {
            case ValueType.BOOLEAN:
                return ((BooleanEncodedValue) encodedValue).getValue();
            case ValueType.BYTE:
                return ((ByteEncodedValue) encodedValue).getValue();
            case ValueType.SHORT:
                return ((ShortEncodedValue) encodedValue).getValue();
            case ValueType.CHAR:
                return ((CharEncodedValue) encodedValue).getValue();
            case ValueType.INT:
                return ((IntEncodedValue) encodedValue).getValue();
            case ValueType.FLOAT:
                return ((FloatEncodedValue) encodedValue).getValue();
            case ValueType.LONG:
                return ((LongEncodedValue) encodedValue).getValue();
            case ValueType.DOUBLE:
                return ((DoubleEncodedValue) encodedValue).getValue();
            case ValueType.STRING:
                return ((StringEncodedValue) encodedValue).getValue();
            case ValueType.TYPE:
                return Type.getType(((TypeEncodedValue) encodedValue).getValue());
            case ValueType.NULL:
                return null;
            default:
                throw new AssertionError(encodedValue.getClass().getName());
        }
    }

    private static boolean isInaccessible(int accessFlags) {
        if ((accessFlags & ACC_SYNTHETIC) != 0) return true;
        return (accessFlags & ACC_PUBLIC) == 0 && (accessFlags & ACC_PROTECTED) == 0;
    }

    private static boolean isRecord(DexBackedClassDef classDef) {
        for (Annotation annotation : classDef.getAnnotations()) {
            if (TypeUtils.TYPE_DALVIK_RECORD.equals(annotation.getType())) return true;
        }
        return false;
    }

    private static boolean isObjectMethod(Method method) {
        if ((method.getAccessFlags() & ACC_STATIC) != 0) return false;
        if (TypeUtils.TYPE_OBJECT.equals(method.getDefiningClass())) return false;
        List<? extends CharSequence> parameterTypes = method.getParameterTypes();
        int parameterCount = parameterTypes.size();
        String returnType = method.getReturnType();
        switch (method.getName()) {
            case "clone":
                return parameterCount == 0 && TypeUtils.TYPE_OBJECT.equals(returnType);
            case "equals":
                return parameterCount == 1
                        && TypeUtils.TYPE_OBJECT.equals(parameterTypes.get(0).toString())
                        && "Z".equals(returnType);
            case "finalize":
                return parameterCount == 0 && "V".equals(returnType);
            case "hashCode":
                return parameterCount == 0 && "I".equals(returnType);
            case "toString":
                return parameterCount == 0 && TypeUtils.TYPE_STRING.equals(returnType);
            default:
                return false;
        }
    }

    private static boolean isVisible(Annotation annotation) {
        return (annotation.getVisibility() & AnnotationVisibility.RUNTIME) != 0;
    }

    private static HashMap<String, DexBackedClassDef> searchReferencedInaccessibleClasses(
            HashMap<String, DexBackedClassDef> classDefs) {
        HashMap<String, DexBackedClassDef> referenced = new HashMap<>();
        for (DexBackedClassDef classDef : classDefs.values()) {
            if (isInaccessible(classDef.getAccessFlags())) continue;
            addToReferencedRecursive(referenced, classDefs, classDef.getSuperclass());
            addToReferencedRecursive(referenced, classDefs, classDef.getAnnotations());
            for (String i : classDef.getInterfaces()) {
                addToReferencedRecursive(referenced, classDefs, i);
            }
            for (DexBackedField field : classDef.getFields()) {
                if (isInaccessible(field.getAccessFlags())) continue;
                addToReferencedRecursive(referenced, classDefs, field.getType());
                addToReferencedRecursive(referenced, classDefs, field.getAnnotations());
            }
            for (DexBackedMethod method : classDef.getMethods()) {
                if (isInaccessible(method.getAccessFlags())) continue;
                addToReferencedRecursive(referenced, classDefs, method.getReturnType());
                for (MethodParameter param : method.getParameters()) {
                    addToReferencedRecursive(referenced, classDefs, param.getType());
                    addToReferencedRecursive(referenced, classDefs, param.getAnnotations());
                }
                addToReferencedRecursive(referenced, classDefs, method.getAnnotations());
            }
        }
        HashMap<String, DexBackedClassDef> referencedClassDefsInInaccessibleClassDefs =
                new HashMap<>();
        for (DexBackedClassDef classDef : referenced.values()) {
            addToReferenced(
                    referencedClassDefsInInaccessibleClassDefs,
                    classDefs,
                    classDef.getSuperclass());
            for (String i : classDef.getInterfaces()) {
                addToReferenced(referencedClassDefsInInaccessibleClassDefs, classDefs, i);
            }
        }
        referenced.putAll(referencedClassDefsInInaccessibleClassDefs);
        return referenced;
    }

    private static void addToReferencedRecursive(
            HashMap<String, DexBackedClassDef> referenced,
            HashMap<String, DexBackedClassDef> classDefs,
            String type) {
        DexBackedClassDef classDef = classDefs.get(TypeUtils.getComponentType(type));
        if (classDef == null) return;
        if (!isInaccessible(classDef.getAccessFlags())) return;
        referenced.put(classDef.getType(), classDef);
        addToReferencedRecursive(referenced, classDefs, classDef.getSuperclass());
        for (String i : classDef.getInterfaces()) {
            addToReferencedRecursive(referenced, classDefs, i);
        }
    }

    private static void addToReferencedRecursive(
            HashMap<String, DexBackedClassDef> referenced,
            HashMap<String, DexBackedClassDef> classDefs,
            Set<? extends Annotation> annotations) {
        for (Annotation annotation : annotations) {
            String type = annotation.getType();
            switch (type) {
                case TypeUtils.TYPE_DALVIK_SIGNATURE:
                case TypeUtils.TYPE_DALVIK_THROWS:
                case TypeUtils.TYPE_DALVIK_PERMITTED_SUBCLASSES:
                    break;
                default:
                    if (type.startsWith("Ldalvik/annotation")) continue;
            }
            addToReferencedRecursive(referenced, classDefs, type);
            for (AnnotationElement element : annotation.getElements()) {
                addToReferencedRecursive(referenced, classDefs, element.getValue());
            }
        }
    }

    private static void addToReferencedRecursive(
            HashMap<String, DexBackedClassDef> referenced,
            HashMap<String, DexBackedClassDef> classDefs,
            EncodedValue encodedValue) {
        switch (encodedValue.getValueType()) {
            case ValueType.ANNOTATION:
                {
                    AnnotationEncodedValue v = (AnnotationEncodedValue) encodedValue;
                    addToReferencedRecursive(referenced, classDefs, v.getType());
                    for (AnnotationElement element : v.getElements()) {
                        addToReferencedRecursive(referenced, classDefs, element.getValue());
                    }
                    break;
                }
            case ValueType.ARRAY:
                {
                    ArrayEncodedValue v = (ArrayEncodedValue) encodedValue;
                    for (EncodedValue childValue : v.getValue()) {
                        addToReferencedRecursive(referenced, classDefs, childValue);
                    }
                    break;
                }
            case ValueType.TYPE:
                {
                    TypeEncodedValue v = (TypeEncodedValue) encodedValue;
                    addToReferencedRecursive(referenced, classDefs, v.getValue());
                    break;
                }
            case ValueType.ENUM:
                {
                    FieldReference v = ((EnumEncodedValue) encodedValue).getValue();
                    addToReferencedRecursive(referenced, classDefs, v.getDefiningClass());
                    addToReferencedRecursive(referenced, classDefs, v.getType());
                    break;
                }
            case ValueType.FIELD:
                {
                    FieldReference v = ((FieldEncodedValue) encodedValue).getValue();
                    addToReferencedRecursive(referenced, classDefs, v.getDefiningClass());
                    addToReferencedRecursive(referenced, classDefs, v.getType());
                    break;
                }
            case ValueType.METHOD:
                {
                    MethodReference v = ((MethodEncodedValue) encodedValue).getValue();
                    addToReferencedRecursive(referenced, classDefs, v.getDefiningClass());
                    addToReferencedRecursive(referenced, classDefs, v.getReturnType());
                    for (CharSequence parameterType : v.getParameterTypes()) {
                        addToReferencedRecursive(
                                referenced, classDefs, String.valueOf(parameterType));
                    }
                }
            default:
        }
    }

    private static void addToReferenced(
            HashMap<String, DexBackedClassDef> referenced,
            HashMap<String, DexBackedClassDef> classDefs,
            String type) {
        DexBackedClassDef classDef = classDefs.get(TypeUtils.getComponentType(type));
        if (classDef == null) return;
        if (!isInaccessible(classDef.getAccessFlags())) return;
        referenced.put(classDef.getType(), classDef);
    }

    private static int getAndFixDexAccessFlags(DexBackedClassDef classDef) {
        int accessFlags = classDef.getAccessFlags();
        if ((accessFlags & ACC_INTERFACE) == 0) {
            accessFlags |= ACC_SUPER;
        }

        boolean isRecord = isRecord(classDef);
        if (isRecord) {
            accessFlags |= ACC_RECORD;
        }

        if (isDeprecated(classDef.getAnnotations())) {
            accessFlags |= ACC_DEPRECATED;
        } else {
            accessFlags &= ~ACC_DEPRECATED;
        }
        return accessFlags;
    }

    private static boolean isDeprecated(Set<? extends Annotation> annotations) {
        for (Annotation annotation : annotations) {
            if (TypeUtils.TYPE_DEPRECATED.equals(annotation.getType())) return true;
        }
        return false;
    }

    private static List<MethodParameterRecord> getParameters(DexBackedMethod method) {
        ArrayList<MethodParameterRecord> result = new ArrayList<>();
        boolean hasEmptyParameterName = false;
        int id = (method.getAccessFlags() & ACC_STATIC) != 0 ? 0 : 1;
        for (MethodParameter parameter : method.getParameters()) {
            String name = parameter.getName();
            String type = parameter.getType();
            if (name == null) {
                hasEmptyParameterName = true;
            }
            result.add(
                    new MethodParameterRecord(
                            id, name, type, parameter.getSignature(), parameter.getAnnotations()));
            char c = type.charAt(0);
            id += c == 'J' || c == 'D' ? 2 : 1;
        }

        if (result.isEmpty()
                || !hasEmptyParameterName
                || (method.getAccessFlags() & ACC_ABSTRACT) != 0) return result;
        id = 1;
        for (int i = result.size() - 1; i != -1; i--) {
            MethodParameterRecord r = result.get(i);
            r.registerId = id;
            char c = r.type.charAt(0);
            id += c == 'J' || c == 'D' ? 2 : 1;
        }

        MethodImplementation implementation = method.getImplementation();
        assert implementation != null;
        int registerCount = implementation.getRegisterCount();

        for (DebugItem debugItem : implementation.getDebugItems()) {
            if (!(debugItem instanceof StartLocal)) continue;
            StartLocal startLocal = (StartLocal) debugItem;

            id = registerCount - startLocal.getRegister();
            hasEmptyParameterName = false;

            for (MethodParameterRecord r : result) {
                if (r.name != null) continue;
                if (r.registerId != id || !r.type.equals(startLocal.getType())) {
                    hasEmptyParameterName = true;
                    continue;
                }
                r.name = startLocal.getName();
                r.signature = startLocal.getSignature();
            }

            if (!hasEmptyParameterName) {
                break;
            }
        }

        return result;
    }

    public void extract(List<String> jarPaths) throws IOException {
        HashMap<String, DexBackedClassDef> classDefs = new HashMap<>();
        for (String jarPath : jarPaths) {
            System.out.println("Extract: " + jarPath);
            MultiDexContainer<? extends DexBackedDexFile> container =
                    DexFileFactory.loadDexContainer(new File(jarPath), dexOpcodes);
            for (String entryName : container.getDexEntryNames()) {
                MultiDexContainer.DexEntry<? extends DexBackedDexFile> entry =
                        container.getEntry(entryName);
                assert entry != null;
                DexBackedDexFile dex = entry.getDexFile();
                for (DexBackedClassDef classDef : dex.getClasses()) {
                    if ((classDef.getAccessFlags() & ACC_SYNTHETIC) != 0) {
                        // To save memory
                        continue;
                    }
                    String type = classDef.getType();
                    DexBackedClassDef previous = classDefs.put(type, classDef);
                    if (previous == null) continue;
                    classDefs.put(type, previous);
                }
            }
        }

        System.out.println("Searching for referenced inaccessible classes");
        HashMap<String, DexBackedClassDef> referenced =
                searchReferencedInaccessibleClasses(classDefs);

        System.out.println("Transforming " + classDefs.size() + " classes");
        for (DexBackedClassDef classDef : classDefs.values()) {
            transformClass(classDef, classDefs, referenced);
        }

        System.out.println("Transforming " + referenced.size() + " inaccessible classes");
        for (DexBackedClassDef classDef : referenced.values()) {
            transformInaccessibleClass(classDef);
        }

        if (hasHiddenApiRestrictions) {
            addHiddenApiRestrictionAnnotation();
        }
    }

    private void transformClass(
            DexBackedClassDef classDef,
            HashMap<String, DexBackedClassDef> classDefs,
            HashMap<String, DexBackedClassDef> referenced) {
        int accessFlags = classDef.getAccessFlags();
        if ((accessFlags & ACC_PUBLIC) == 0) return;

        InnerClassRecord innerClass = InnerClassRecord.findIn(classDef);
        if (innerClass != null && isInaccessible(innerClass.accessFlags)) {
            return;
        }
        accessFlags = getAndFixDexAccessFlags(classDef);

        String name = TypeUtils.toCfName(classDef.getType());
        String superclass = classDef.getSuperclass();
        if (superclass != null) {
            superclass = TypeUtils.toCfName(superclass);
        } else if (MAKE_TEST_JAR_FOR_JVM) return;

        if (MAKE_TEST_JAR_FOR_JVM) {
            String type = classDef.getType();
            if (TypeUtils.TYPE_STRING.equals(type)
                    || TypeUtils.TYPE_THROWABLE.equals(type)
                    || TypeUtils.TYPE_ANNOTATION.equals(type)) {
                return;
            }
        }

        ClassNode classNode = new ClassNode();
        classNode.version = (accessFlags & ACC_RECORD) != 0 ? V17 : V1_8;
        classNode.access = accessFlags;
        classNode.name = name;
        classNode.signature = TypeUtils.getSignature(classDef.getAnnotations());
        classNode.superName = superclass;
        classNode.interfaces = Arrays.asList(TypeUtils.toCfName(classDef.getInterfaces()));
        classNode.sourceFile = classDef.getSourceFile();

        for (DexBackedAnnotation annotation : classDef.getAnnotations()) {
            transformClassAnnotation(classNode, classDef, annotation, classDefs, referenced);
        }

        for (DexBackedField field : classDef.getFields()) {
            transformField(classNode, field);
        }

        for (DexBackedMethod method : classDef.getMethods()) {
            transformMethod(classNode, method);
        }

        if (MAKE_STUB_METHOD && !MAKE_TEST_JAR_FOR_JVM) {
            MethodVisitor methodVisitor =
                    classNode.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
            methodVisitor.visitCode();
            methodVisitor.visitTypeInsn(NEW, "java/lang/RuntimeException");
            methodVisitor.visitInsn(DUP);
            methodVisitor.visitLdcInsn("Stub!");
            methodVisitor.visitMethodInsn(
                    INVOKESPECIAL,
                    "java/lang/RuntimeException",
                    "<init>",
                    "(Ljava/lang/String;)V",
                    false);
            methodVisitor.visitInsn(ATHROW);
            methodVisitor.visitMaxs(3, 0);
            methodVisitor.visitEnd();
        }

        classNode.visitEnd();
        ClassWriter classWriter = new ClassWriter(0);
        classNode.accept(classWriter);

        if (classes.put(name + ".class", classWriter.toByteArray()) != null) {
            System.err.println("Duplicated class: " + name);
        }
    }

    private void transformInaccessibleClass(DexBackedClassDef classDef) {
        InnerClassRecord innerClass = InnerClassRecord.findIn(classDef);
        if (innerClass != null && isInaccessible(innerClass.accessFlags)) {
            return;
        }

        int accessFlags = getAndFixDexAccessFlags(classDef);
        String name = TypeUtils.toCfName(classDef.getType());
        ClassWriter classWriter = new ClassWriter(0);
        classWriter.visit(
                (accessFlags & ACC_RECORD) != 0 ? V17 : V1_8,
                accessFlags,
                name,
                TypeUtils.getSignature(classDef.getAnnotations()),
                TypeUtils.toCfName(classDef.getSuperclass()),
                TypeUtils.toCfName(classDef.getInterfaces()));
        classWriter.visitEnd();
        if (inaccessibleClasses.put(name + ".class", classWriter.toByteArray()) != null) {
            System.err.println("Duplicated inaccessible class: " + name);
        }
    }

    private void transformClassAnnotation(
            ClassNode classNode,
            DexBackedClassDef classDef,
            DexBackedAnnotation annotation,
            HashMap<String, DexBackedClassDef> classDefs,
            HashMap<String, DexBackedClassDef> referenced) {
        switch (annotation.getType()) {
            case TypeUtils.TYPE_DALVIK_ENCLOSING_METHOD:
                {
                    MethodReference v =
                            ((MethodEncodedValue) TypeUtils.assertAndGetSingleValue(annotation))
                                    .getValue();
                    classNode.outerClass = TypeUtils.toCfName(v.getDefiningClass());
                    classNode.outerMethod = v.getName();
                    classNode.outerMethodDesc = TypeUtils.getMethodDescriptor(v);
                    break;
                }
            case TypeUtils.TYPE_DALVIK_INNER_CLASS:
                {
                    InnerClassRecord inner = InnerClassRecord.findIn(annotation);
                    if (inner == null) break;
                    if (isInaccessible(inner.accessFlags) && referenced.get(inner.name) == null)
                        break;
                    String name = TypeUtils.toCfName(classDef.getType());
                    classNode.innerClasses.add(
                            new InnerClassNode(
                                    name,
                                    TypeUtils.getOuterName(name),
                                    inner.name,
                                    inner.accessFlags));
                    break;
                }
            case TypeUtils.TYPE_DALVIK_MEMBER_CLASSES:
                {
                    ArrayEncodedValue v =
                            (ArrayEncodedValue) TypeUtils.assertAndGetSingleValue(annotation);
                    for (EncodedValue childValue : v.getValue()) {
                        String memberType = ((TypeEncodedValue) childValue).getValue();
                        String memberCfName = TypeUtils.toCfName(memberType);
                        DexBackedClassDef memberClassDef = classDefs.get(memberType);
                        if (memberClassDef == null) {
                            System.err.println("Class not found: " + memberCfName);
                            continue;
                        }
                        InnerClassRecord inner = InnerClassRecord.findIn(memberClassDef);
                        if (inner == null) continue;
                        if (isInaccessible(inner.accessFlags) && referenced.get(inner.name) == null)
                            continue;
                        classNode.innerClasses.add(
                                new InnerClassNode(
                                        memberCfName,
                                        classNode.name,
                                        inner.name,
                                        inner.accessFlags));
                    }
                    break;
                }
            case TypeUtils.TYPE_DALVIK_PERMITTED_SUBCLASSES:
                {
                    ArrayEncodedValue v =
                            (ArrayEncodedValue) TypeUtils.assertAndGetSingleValue(annotation);
                    classNode.permittedSubclasses = new ArrayList<>();
                    for (EncodedValue childValue : v.getValue()) {
                        classNode.permittedSubclasses.add(
                                TypeUtils.toCfName(((TypeEncodedValue) childValue).getValue()));
                    }
                    break;
                }
            case TypeUtils.TYPE_DALVIK_RECORD:
                {
                    System.err.println("Record: " + classDef.getType());
                    // TODO: 实现 java 17 record 转换
                    break;
                }
            case TypeUtils.TYPE_DALVIK_ENCLOSING_CLASS:
            case TypeUtils.TYPE_DALVIK_SIGNATURE:
            case TypeUtils.TYPE_DALVIK_THROWS:
            case TypeUtils.TYPE_DALVIK_ANNOTATION_DEFAULT:
                break;
            default:
                transformAnnotationElement(
                        classNode.visitAnnotation(annotation.getType(), isVisible(annotation)),
                        annotation);
        }
    }

    private void transformField(ClassNode classNode, DexBackedField field) {
        int accessFlags = field.getAccessFlags();
        if (isInaccessible(accessFlags)) return;

        Object initialValue = null;
        if ((accessFlags & ACC_FINAL) != 0
                && !field.getDefiningClass().startsWith("Lcom/android/internal/R$")) {
            initialValue = toAsmValue(field.getInitialValue());
        }

        if (isDeprecated(field.getAnnotations())) {
            accessFlags |= ACC_DEPRECATED;
        } else {
            accessFlags &= ~ACC_DEPRECATED;
        }

        FieldVisitor fieldVisitor =
                classNode.visitField(
                        accessFlags,
                        field.getName(),
                        TypeUtils.wrapType(field.getType()),
                        TypeUtils.getSignature(field.getAnnotations()),
                        initialValue);
        transformFieldAnnotation(fieldVisitor, field);
        fieldVisitor.visitEnd();
    }

    private void transformFieldAnnotation(FieldVisitor fieldVisitor, DexBackedField field) {
        for (Annotation annotation : field.getAnnotations()) {
            if (TypeUtils.TYPE_DALVIK_SIGNATURE.equals(annotation.getType())) continue;
            transformAnnotationElement(
                    fieldVisitor.visitAnnotation(
                            TypeUtils.wrapType(annotation.getType()), isVisible(annotation)),
                    annotation);
        }
        if (field.getInitialValue() != null) return;
        if (!TRANSFORM_HIDDEN_API_RESTRICTION) return;
        Set<? extends HiddenApiRestriction> hiddenApiRestrictions =
                field.getHiddenApiRestrictions();
        if (!hiddenApiRestrictions.isEmpty()
                && !hiddenApiRestrictions.contains(HiddenApiRestriction.WHITELIST)) {
            transformHiddenApiRestrictions(
                    fieldVisitor.visitAnnotation(TYPE_HIDDEN_API_RESTRICTION, false),
                    hiddenApiRestrictions);
        }
    }

    private void transformHiddenApiRestrictions(
            AnnotationVisitor annotationVisitor,
            Set<? extends HiddenApiRestriction> hiddenApiRestrictions) {
        AnnotationVisitor childVisitor =
                annotationVisitor.visitArray(FIELD_HIDDEN_API_RESTRICTION_VALUE);
        for (HiddenApiRestriction hiddenApiRestriction : hiddenApiRestrictions) {
            childVisitor.visit(null, hiddenApiRestriction.toString());
        }
        childVisitor.visitEnd();
        annotationVisitor.visitEnd();
        hasHiddenApiRestrictions = true;
    }

    private void transformMethod(ClassNode classNode, DexBackedMethod method) {
        int accessFlags = method.getAccessFlags();
        if (isInaccessible(accessFlags)
                || (accessFlags & ACC_BRIDGE) != 0
                || isObjectMethod(method)) return;
        if ("<clinit>".equals(method.getName())) return;

        Set<? extends Annotation> annotations = method.getAnnotations();
        if (isDeprecated(annotations)) {
            accessFlags |= ACC_DEPRECATED;
        } else {
            accessFlags &= ~ACC_DEPRECATED;
        }

        MethodImplementation implementation = method.getImplementation();
        if (implementation != null
                && (method.getAccessFlags() & 0x20000 /* declared-synchronized */) != 0) {
            for (Instruction instruction : implementation.getInstructions()) {
                if (instruction.getOpcode() != Opcode.MONITOR_ENTER) continue;
                accessFlags |= ACC_SYNCHRONIZED;
                break;
            }
        }

        MethodVisitor methodVisitor =
                classNode.visitMethod(
                        accessFlags,
                        method.getName(),
                        TypeUtils.getMethodDescriptor(method),
                        TypeUtils.getSignature(annotations),
                        TypeUtils.getExceptions(annotations));
        transformMethodAnnotation(methodVisitor, method);
        if (implementation != null) {
            transformMethodImplementation(methodVisitor, method);
        }
        methodVisitor.visitEnd();
    }

    private void transformMethodAnnotation(MethodVisitor methodVisitor, DexBackedMethod method) {
        if ((method.classDef.getAccessFlags() & ACC_ANNOTATION) != 0) {
            annotation:
            for (Annotation annotation : method.classDef.getAnnotations()) {
                if (!TypeUtils.TYPE_DALVIK_ANNOTATION_DEFAULT.equals(annotation.getType()))
                    continue;
                AnnotationEncodedValue v =
                        (AnnotationEncodedValue) TypeUtils.assertAndGetSingleValue(annotation);
                for (AnnotationElement element : v.getElements()) {
                    if (!element.getName().equals(method.getName())) continue;
                    AnnotationVisitor annotationVisitor = methodVisitor.visitAnnotationDefault();
                    transformEncodedValue(annotationVisitor, element.getValue(), null);
                    annotationVisitor.visitEnd();
                    break annotation;
                }
            }
        }
        for (Annotation annotation : method.getAnnotations()) {
            switch (annotation.getType()) {
                case TypeUtils.TYPE_DALVIK_SIGNATURE:
                case TypeUtils.TYPE_DALVIK_THROWS:
                case TypeUtils.TYPE_DALVIK_METHOD_PARAMETERS:
                    break;
                default:
                    transformAnnotationElement(
                            methodVisitor.visitAnnotation(
                                    TypeUtils.wrapType(annotation.getType()),
                                    isVisible(annotation)),
                            annotation);
            }
        }
        if (!TRANSFORM_HIDDEN_API_RESTRICTION) return;
        Set<? extends HiddenApiRestriction> hiddenApiRestrictions =
                method.getHiddenApiRestrictions();
        if (!hiddenApiRestrictions.isEmpty()
                && !hiddenApiRestrictions.contains(HiddenApiRestriction.WHITELIST)) {
            transformHiddenApiRestrictions(
                    methodVisitor.visitAnnotation(TYPE_HIDDEN_API_RESTRICTION, false),
                    hiddenApiRestrictions);
        }
    }

    private void transformMethodImplementation(
            MethodVisitor methodVisitor, DexBackedMethod method) {
        List<MethodParameterRecord> parameters = getParameters(method);
        for (MethodParameterRecord param : parameters) {
            if (param.name == null) break;
            methodVisitor.visitParameter(param.name, 0);
        }
        methodVisitor.visitCode();
        Label labelStart = new Label();
        methodVisitor.visitLabel(labelStart);
        int maxStack = 0;
        if ("<init>".equals(method.getName())
                && "V".equals(method.getReturnType())
                && !TypeUtils.TYPE_OBJECT.equals(method.getDefiningClass())) {
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitMethodInsn(
                    INVOKESPECIAL,
                    TypeUtils.toCfName(method.classDef.getSuperclass()),
                    "<init>",
                    "()V",
                    false);
            methodVisitor.visitInsn(RETURN);
            maxStack = 1;
        } else
            switch (method.getReturnType().charAt(0)) {
                case 'Z':
                case 'B':
                case 'S':
                case 'C':
                case 'I':
                    {
                        methodVisitor.visitInsn(ICONST_0);
                        methodVisitor.visitInsn(IRETURN);
                        maxStack = 1;
                        break;
                    }
                case 'F':
                    {
                        methodVisitor.visitInsn(FCONST_0);
                        methodVisitor.visitInsn(FRETURN);
                        maxStack = 1;
                        break;
                    }
                case 'J':
                    {
                        methodVisitor.visitInsn(LCONST_0);
                        methodVisitor.visitInsn(LRETURN);
                        maxStack = 2;
                        break;
                    }
                case 'D':
                    {
                        methodVisitor.visitInsn(DCONST_0);
                        methodVisitor.visitInsn(DRETURN);
                        maxStack = 2;
                        break;
                    }
                case 'V':
                    {
                        methodVisitor.visitInsn(RETURN);
                        break;
                    }
                default:
                    {
                        methodVisitor.visitInsn(ACONST_NULL);
                        methodVisitor.visitInsn(ARETURN);
                        maxStack = 1;
                        break;
                    }
            }
        Label labelEnd = new Label();
        methodVisitor.visitLabel(labelEnd);
        boolean isStatic = (method.getAccessFlags() & ACC_STATIC) != 0;
        if (!isStatic) {
            methodVisitor.visitLocalVariable(
                    "this", method.getDefiningClass(), null, labelStart, labelEnd, 0);
        }
        int paramIndex = 0;
        for (MethodParameterRecord param : parameters) {
            String paramName = param.name;
            String paramSignature = param.signature;

            if (paramName != null || paramSignature != null) {
                if (paramName == null) {
                    paramName = "p" + paramIndex;
                }
                methodVisitor.visitLocalVariable(
                        paramName,
                        TypeUtils.wrapType(param.type),
                        paramSignature,
                        labelStart,
                        labelEnd,
                        param.id);
                transformMethodParameterAnnotation(methodVisitor, param, paramIndex++);
            } else ++paramIndex;
        }
        int parameterCount = parameters.size();
        int maxLocals =
                parameterCount != 0 ? parameters.get(parameterCount - 1).id : isStatic ? 0 : 1;
        methodVisitor.visitMaxs(maxStack, maxLocals);
    }

    private void transformMethodParameterAnnotation(
            MethodVisitor methodVisitor, MethodParameterRecord param, int paramIndex) {
        for (Annotation annotation : param.annotations) {
            if (TypeUtils.TYPE_DALVIK_SIGNATURE.equals(annotation.getType())) continue;
            transformAnnotationElement(
                    methodVisitor.visitParameterAnnotation(
                            paramIndex,
                            TypeUtils.wrapType(annotation.getType()),
                            isVisible(annotation)),
                    annotation);
        }
    }

    private void transformAnnotationElement(
            AnnotationVisitor annotationVisitor, BasicAnnotation annotation) {
        for (AnnotationElement element : annotation.getElements()) {
            EncodedValue encodedValue = element.getValue();
            transformEncodedValue(annotationVisitor, encodedValue, element.getName());
        }
        annotationVisitor.visitEnd();
    }

    private void transformEncodedValue(
            AnnotationVisitor annotationVisitor, EncodedValue encodedValue, String name) {
        switch (encodedValue.getValueType()) {
            case ValueType.ANNOTATION:
                {
                    AnnotationEncodedValue v = (AnnotationEncodedValue) encodedValue;
                    transformAnnotationElement(
                            annotationVisitor.visitAnnotation(
                                    name, TypeUtils.wrapType(v.getType())),
                            v);
                    break;
                }
            case ValueType.ARRAY:
                {
                    ArrayEncodedValue v = (ArrayEncodedValue) encodedValue;
                    AnnotationVisitor childVisitor = annotationVisitor.visitArray(name);
                    for (EncodedValue childValue : v.getValue()) {
                        transformEncodedValue(childVisitor, childValue, null);
                    }
                    childVisitor.visitEnd();
                    break;
                }
            case ValueType.BOOLEAN:
                {
                    BooleanEncodedValue v = (BooleanEncodedValue) encodedValue;
                    annotationVisitor.visit(name, v.getValue());
                    break;
                }
            case ValueType.BYTE:
                {
                    ByteEncodedValue v = (ByteEncodedValue) encodedValue;
                    annotationVisitor.visit(name, v.getValue());
                    break;
                }
            case ValueType.CHAR:
                {
                    CharEncodedValue v = (CharEncodedValue) encodedValue;
                    annotationVisitor.visit(name, v.getValue());
                    break;
                }
            case ValueType.DOUBLE:
                {
                    DoubleEncodedValue v = (DoubleEncodedValue) encodedValue;
                    annotationVisitor.visit(name, v.getValue());
                    break;
                }
            case ValueType.ENUM:
                {
                    FieldReference value = ((EnumEncodedValue) encodedValue).getValue();
                    annotationVisitor.visitEnum(name, value.getDefiningClass(), value.getName());
                    break;
                }
            case ValueType.FLOAT:
                {
                    FloatEncodedValue v = (FloatEncodedValue) encodedValue;
                    annotationVisitor.visit(name, v.getValue());
                    break;
                }
            case ValueType.INT:
                {
                    IntEncodedValue v = (IntEncodedValue) encodedValue;
                    annotationVisitor.visit(name, v.getValue());
                    break;
                }
            case ValueType.LONG:
                {
                    LongEncodedValue v = (LongEncodedValue) encodedValue;
                    annotationVisitor.visit(name, v.getValue());
                    break;
                }
            case ValueType.SHORT:
                {
                    ShortEncodedValue v = (ShortEncodedValue) encodedValue;
                    annotationVisitor.visit(name, v.getValue());
                    break;
                }
            case ValueType.STRING:
                {
                    StringEncodedValue v = (StringEncodedValue) encodedValue;
                    annotationVisitor.visit(name, v.getValue());
                    break;
                }
            case ValueType.TYPE:
                {
                    TypeEncodedValue v = (TypeEncodedValue) encodedValue;
                    annotationVisitor.visit(name, Type.getType(v.getValue()));
                    break;
                }
            default:
                {
                    throw new IllegalArgumentException(encodedValue.getClass().getName());
                }
        }
    }

    private void addHiddenApiRestrictionAnnotation() {
        ClassWriter classWriter = new ClassWriter(0);
        int acc = ACC_ANNOTATION | ACC_ABSTRACT | ACC_INTERFACE;
        if (MAKE_TEST_JAR_FOR_JVM) {
            acc |= ACC_PUBLIC;
        }
        String name = TypeUtils.toCfName(TYPE_HIDDEN_API_RESTRICTION);
        classWriter.visit(
                V1_8,
                acc,
                name,
                null,
                "java/lang/Object",
                new String[] {"java/lang/annotation/Annotation"});
        MethodVisitor methodVisitor =
                classWriter.visitMethod(
                        ACC_PUBLIC | ACC_ABSTRACT,
                        FIELD_HIDDEN_API_RESTRICTION_VALUE,
                        "()[Ljava/lang/String;",
                        null,
                        null);
        methodVisitor.visitEnd();
        classWriter.visitEnd();
        classes.put(name + ".class", classWriter.toByteArray());
    }

    public void writeTo(OutputStream out) throws IOException {
        try (JarOutputStream jar = new JarOutputStream(out)) {
            writeToJar(jar);
        }
    }

    private void writeToJar(JarOutputStream jar) throws IOException {
        jar.setLevel(9);

        System.out.println("Writing " + classes.size() + " classes");
        for (Map.Entry<String, byte[]> e : classes.entrySet()) {
            ZipEntry zipEntry = new ZipEntry(e.getKey());
            zipEntry.setTime(0);
            jar.putNextEntry(zipEntry);
            jar.write(e.getValue());
        }

        System.out.println("Writing " + inaccessibleClasses.size() + " inaccessible classes");
        for (Map.Entry<String, byte[]> e : inaccessibleClasses.entrySet()) {
            ZipEntry zipEntry = new ZipEntry(e.getKey());
            zipEntry.setTime(0);
            jar.putNextEntry(zipEntry);
            jar.write(e.getValue());
        }

        if (!APPEND_RESOURCE_FILES && !APPEND_RESOURCE_BLOCKS) return;
        System.out.println("Writing resources");

        //noinspection IOStreamConstructor
        try (ZipInputStream zip =
                new ZipInputStream(
                        new BufferedInputStream(
                                new FileInputStream("/system/framework/framework-res.apk")))) {
            ZipEntry zipEntry;
            while ((zipEntry = zip.getNextEntry()) != null) {
                String name = zipEntry.getName();
                if ((APPEND_RESOURCE_BLOCKS
                                && ("resources.arsc".equals(name)
                                        || "AndroidManifest.xml".equals(name)))
                        || (APPEND_RESOURCE_FILES
                                && (name.startsWith("assets/") || name.startsWith("res/")))) {
                    zipEntry.setTime(0);
                    jar.putNextEntry(zipEntry);
                    ByteStreams.copy(zip, jar);
                }
            }
        }
    }
}
