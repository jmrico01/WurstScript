package de.peeeq.wurstio.objectreader;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ObjectTable {

    private Map<Integer, ObjectDefinition> objectDefinitions = new HashMap<>();
    private ObjectFileType fileType;

    public ObjectTable(ObjectFileType fileType2) {
        this.fileType = fileType2;
    }

    public void add(ObjectDefinition objDef) {
        objectDefinitions.put(objDef.getNewObjectId(), objDef);
    }

    static ObjectTable readFromStream(BinaryDataInputStream in, ObjectFileType fileType) throws IOException {
        ObjectTable objectTable = new ObjectTable(fileType);


        int numberOfObjects = in.readInt();

        for (int i = 0; i < numberOfObjects; i++) {
            ObjectDefinition objDef = ObjectDefinition.readFromStream(in, objectTable);
            objectTable.add(objDef);
        }

        return objectTable;
    }


    public Map<Integer, ObjectDefinition>  getObjectDefinitions() {
        return objectDefinitions;
    }

    public void writeToStream(BinaryDataOutputStream out, ObjectFileType fileType) throws IOException {

        // write number of objects
        out.writeInt(objectDefinitions.size());

        for (ObjectDefinition od : objectDefinitions.values()) {
            od.writeToStream(out, fileType);
        }

    }

    public void prettyPrint(StringBuilder sb) {
        for (ObjectDefinition od : objectDefinitions.values()) {
            od.prettyPrint(sb);
        }

    }

    public void exportToWurst(Appendable out) throws IOException {
        for (ObjectDefinition od : objectDefinitions.values()) {
            od.exportToWurst(out);
        }

    }

    public ObjectFileType getFileType() {
        return fileType;
    }

    public boolean isEmpty() {
        return objectDefinitions.isEmpty();
    }

}
