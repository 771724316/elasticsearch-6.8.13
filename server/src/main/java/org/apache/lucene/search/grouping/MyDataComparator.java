package org.apache.lucene.search.grouping;

import org.apache.lucene.search.FieldComparator;
import org.apache.lucene.search.FieldDoc;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.SortField;
import org.apache.lucene.util.BytesRef;

import java.util.Comparator;
import java.util.List;

/***
 * 新增的排序类
 */
public class MyDataComparator implements Comparator<ScoreDoc> {

    private List<SortField> sortFieldList;
    private List<String> firstDataList;
    private List<ScoreDoc> dataList;


    private FieldComparator<?>[] comparators;
    private int[] reverseMul;


    public MyDataComparator(List<SortField> sortFieldList, List<String> firstDataList, List<ScoreDoc> dataList) {
        this.sortFieldList = sortFieldList;
        this.firstDataList = firstDataList;
        this.dataList = dataList;
        if (sortFieldList != null) {
            setCompare();
        }
    }


    private void setCompare() {
        int endIndex = sortFieldList.size();
        if (firstDataList != null) {
            endIndex = endIndex - 1;
        }

        comparators = new FieldComparator[endIndex];
        reverseMul = new int[endIndex];

        for (int compIDX = 0; compIDX < endIndex; compIDX++) {
            final SortField sortField = sortFieldList.get(compIDX);
            comparators[compIDX] = sortField.getComparator(1, compIDX);
            reverseMul[compIDX] = sortField.getReverse() ? -1 : 1;
        }
    }


    @Override
    public int compare(ScoreDoc o1, ScoreDoc o2) {
        int result = 0;
        if (firstDataList != null) {
            result = compareLastValue(o1, o2);
        }
        if (result == 0) {
            result = lessThan(o1, o2);
        }
        return result;
    }


    public int lessThan(ScoreDoc first, ScoreDoc second) {
        assert first != second;
        final FieldDoc firstFD = (FieldDoc) first;
        final FieldDoc secondFD = (FieldDoc) second;

        int fieldLength = firstFD.fields.length;
        int compareLength = comparators.length;

        int valueIndex = fieldLength - compareLength;
        if (firstDataList != null) {
            valueIndex = valueIndex - 1;
        }

        for (int compIDX = 0; compIDX < comparators.length; compIDX++) {
            final FieldComparator comp = comparators[compIDX];
            final int cmp = reverseMul[compIDX] * comp.compareValues(firstFD.fields[compIDX + valueIndex], secondFD.fields[compIDX + valueIndex]);
            if (cmp != 0) {
                return cmp;
            }
        }
        return 0;
    }


    public int compareLastValue(ScoreDoc o1, ScoreDoc o2) {
        int v1 = 0;
        int v2 = 0;

        FieldDoc fieldDoc1 = (FieldDoc) o1;
        FieldDoc fieldDoc2 = (FieldDoc) o2;
        Object obj1 = fieldDoc1.fields[fieldDoc1.fields.length - 1];
        Object obj2 = fieldDoc2.fields[fieldDoc2.fields.length - 1];
        if (obj1 instanceof BytesRef) {
            BytesRef bytesRef1 = (BytesRef) obj1;
            BytesRef bytesRef2 = (BytesRef) obj2;
            String s1 = bytesRef1.utf8ToString();
            String s2 = bytesRef2.utf8ToString();

            v1 = getValueWeight(firstDataList, s1);
            v2 = getValueWeight(firstDataList, s2);
        }
        return -Integer.compare(v1, v2);
    }

    public int getValueWeight(List<String> firstDataList, String source) {
        if (firstDataList != null) {
            for (int i = 0; i < firstDataList.size(); i++) {
                if (source != null && source.equals("") == false && firstDataList.get(i).equalsIgnoreCase(source)) {
                    return firstDataList.size() - i;
                }
            }
        }
        return 0;
    }
}
