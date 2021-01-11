/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.lucene.search.grouping;

import org.apache.lucene.search.*;
import org.apache.lucene.util.PriorityQueue;

import java.util.*;

/**
 * Represents hits returned by {@link CollapsingTopDocsCollector#getTopDocs()}.
 */
public final class CollapseTopFieldDocs2 extends TopFieldDocs {
    /**
     * The field used for collapsing
     **/
    public final String field;
    /**
     * The collapse value for each top doc
     */
    public final Object[] collapseValues;

    public List<String> firstDataList;

    public int sortFieldSize;

    public CollapseTopFieldDocs2(String field, long totalHits, ScoreDoc[] scoreDocs,
                                 SortField[] sortFields, Object[] values, float maxScore) {
        super(totalHits, scoreDocs, sortFields, maxScore);
        this.field = field;
        this.collapseValues = values;
    }

    public CollapseTopFieldDocs2(String field, long totalHits, ScoreDoc[] scoreDocs,
                                 SortField[] sortFields, Object[] values, float maxScore, List<String> firstDataList, int sortFieldSize) {
        super(totalHits, scoreDocs, sortFields, maxScore);
        this.field = field;
        this.collapseValues = values;
        this.firstDataList = firstDataList;
        this.sortFieldSize = sortFieldSize;
    }

    // Refers to one hit:
    private static final class ShardRef {
        // Which shard (index into shardHits[]):
        final int shardIndex;

        // True if we should use the incoming ScoreDoc.shardIndex for sort order
        final boolean useScoreDocIndex;

        // Which hit within the shard:
        int hitIndex;

        ShardRef(int shardIndex, boolean useScoreDocIndex) {
            this.shardIndex = shardIndex;
            this.useScoreDocIndex = useScoreDocIndex;
        }

        @Override
        public String toString() {
            return "ShardRef(shardIndex=" + shardIndex + " hitIndex=" + hitIndex + ")";
        }

        int getShardIndex(ScoreDoc scoreDoc) {
            if (useScoreDocIndex) {
                if (scoreDoc.shardIndex == -1) {
                    throw new IllegalArgumentException("setShardIndex is false but TopDocs["
                        + shardIndex + "].scoreDocs[" + hitIndex + "] is not set");
                }
                return scoreDoc.shardIndex;
            } else {
                // NOTE: we don't assert that shardIndex is -1 here, because caller could in fact have set it but asked us to ignore it now
                return shardIndex;
            }
        }
    }

    /**
     * if we need to tie-break since score / sort value are the same we first compare shard index (lower shard wins)
     * and then iff shard index is the same we use the hit index.
     */
    static boolean tieBreakLessThan(ShardRef first, ScoreDoc firstDoc, ShardRef second, ScoreDoc secondDoc) {
        final int firstShardIndex = first.getShardIndex(firstDoc);
        final int secondShardIndex = second.getShardIndex(secondDoc);
        // Tie break: earlier shard wins
        if (firstShardIndex < secondShardIndex) {
            return true;
        } else if (firstShardIndex > secondShardIndex) {
            return false;
        } else {
            // Tie break in same shard: resolve however the
            // shard had resolved it:
            assert first.hitIndex != second.hitIndex;
            return first.hitIndex < second.hitIndex;
        }
    }

    private static class MergeSortQueue extends PriorityQueue<ShardRef> {
        // These are really FieldDoc instances:
        final ScoreDoc[][] shardHits;
        final FieldComparator<?>[] comparators;
        final int[] reverseMul;

        MergeSortQueue(Sort sort, CollapseTopFieldDocs2[] shardHits) {
            super(shardHits.length);
            this.shardHits = new ScoreDoc[shardHits.length][];
            for (int shardIDX = 0; shardIDX < shardHits.length; shardIDX++) {
                final ScoreDoc[] shard = shardHits[shardIDX].scoreDocs;
                if (shard != null) {
                    this.shardHits[shardIDX] = shard;
                    // Fail gracefully if API is misused:
                    for (int hitIDX = 0; hitIDX < shard.length; hitIDX++) {
                        final ScoreDoc sd = shard[hitIDX];
                        final FieldDoc gd = (FieldDoc) sd;
                        assert gd.fields != null;
                    }
                }
            }

            final SortField[] sortFields = sort.getSort();
            comparators = new FieldComparator[sortFields.length];
            reverseMul = new int[sortFields.length];
            for (int compIDX = 0; compIDX < sortFields.length; compIDX++) {
                final SortField sortField = sortFields[compIDX];
                comparators[compIDX] = sortField.getComparator(1, compIDX);
                reverseMul[compIDX] = sortField.getReverse() ? -1 : 1;
            }
        }

        // Returns true if first is < second
        @Override
        public boolean lessThan(ShardRef first, ShardRef second) {
            assert first != second;
            final FieldDoc firstFD = (FieldDoc) shardHits[first.shardIndex][first.hitIndex];
            final FieldDoc secondFD = (FieldDoc) shardHits[second.shardIndex][second.hitIndex];

            for (int compIDX = 0; compIDX < comparators.length; compIDX++) {
                final FieldComparator comp = comparators[compIDX];

                final int cmp =
                    reverseMul[compIDX] * comp.compareValues(firstFD.fields[compIDX], secondFD.fields[compIDX]);

                if (cmp != 0) {
                    return cmp < 0;
                }
            }
            return tieBreakLessThan(first, firstFD, second, secondFD);
        }
    }

    /**
     * Returns a new CollapseTopDocs, containing topN collapsed results across
     * the provided CollapseTopDocs, sorting by score. Each {@link CollapseTopFieldDocs2} instance must be sorted.
     **/
    public static CollapseTopFieldDocs2 merge(Sort sort, int start, int size, CollapseTopFieldDocs2[] shardHits, boolean setShardIndex) {

        Map<Object, List<ScoreDoc>> collapseMap = new LinkedHashMap<>();

        String collapseField = shardHits[0].field;
        List<String> firstDataList = shardHits[0].firstDataList;
        int sortFieldSize = shardHits[0].sortFieldSize;

        long totalHitCount = 0;
        int availHitCount = 0;
        float maxScore = Float.MIN_VALUE;
        for (int shardIDX = 0; shardIDX < shardHits.length; shardIDX++) {
            final CollapseTopFieldDocs2 shard = shardHits[shardIDX];
            // totalHits can be non-zero even if no hits were
            // collected, when searchAfter was used:
            totalHitCount += shard.totalHits;
            if (shard.scoreDocs != null && shard.scoreDocs.length > 0) {
                availHitCount += shard.scoreDocs.length;

                maxScore = Math.max(maxScore, shard.getMaxScore());


                for (int i = 0; i < shardHits[shardIDX].scoreDocs.length; i++) {
                    ScoreDoc hit = shardHits[shardIDX].scoreDocs[i];
                    Object collapseValue = shardHits[shardIDX].collapseValues[i];
                    List<ScoreDoc> mapList = collapseMap.get(collapseValue);
                    if (mapList == null) {
                        mapList = new ArrayList<>();
                    }
                    mapList.add(hit);
                    collapseMap.put(collapseValue, mapList);
                }
            }
        }

        if (availHitCount == 0) {
            maxScore = Float.NaN;
        }

        SortField[] sortFields = sort.getSort();

        List<SortField> topDataSortList = new ArrayList<>();
        for (int i = sortFields.length - sortFieldSize; i < sortFields.length; i++) {  //选出代表的排序字段
            topDataSortList.add(sortFields[i]);
        }


        //下面是新增的代码，筛选出来代表数据、然后把代表数据进行排序
        ScoreDoc[] hits;
        Object[] values;

        List<ScoreDoc> hitList = new ArrayList<>();
        List<Object> collapseList = new ArrayList<>();

        for (Object key : collapseMap.keySet()) {
            List<ScoreDoc> childList = collapseMap.get(key);
            sortAllDataByRules(topDataSortList, firstDataList, childList);  //根据规则选出来每一组数据的代表数据
            hitList.add(childList.get(0));
            collapseList.add(key);
        }


        Map<ScoreDoc, Object> topNMap = new HashMap<>();
        for (int i = 0; i < hitList.size(); i++) {
            topNMap.put(hitList.get(i), collapseList.get(i));
        }


        //代表数据按照原排序规则进行排序--start------------------

        List<SortField> ssList = new ArrayList<>();
        for (int i = 0; i < sortFields.length - sortFieldSize; i++) {
            ssList.add(sortFields[i]);
        }

        if (ssList.size() == 0) {
            ssList.addAll(Arrays.asList(sortFields));
        }
        sort.setSort(ssList.toArray(new SortField[ssList.size()]));

        TopFieldDocs[] tt = new TopFieldDocs[hitList.size()];
        for (int i = 0; i < hitList.size(); i++) {
            tt[i] = new TopFieldDocs(0, new ScoreDoc[]{hitList.get(i)}, ssList.toArray(new SortField[ssList.size()]), maxScore);
        }

        TopFieldDocs topFieldDocs = TopDocs.merge(sort, start, size, tt, setShardIndex); //代表数据按照原排序规则进行排序
        hits = topFieldDocs.scoreDocs;

        //代表数据按照原排序规则进行排序--end------------------

        List<Object> newCollapseList = new ArrayList<>();
        for (ScoreDoc doc : hits) {
            newCollapseList.add(topNMap.get(doc));  //取出代表数据对应的collapse value值
        }
        values = newCollapseList.toArray(new Object[0]);

        return new CollapseTopFieldDocs2(collapseField, totalHitCount, hits, sort.getSort(), values, maxScore);
    }


    //排序
    private static void sortAllDataByRules(List<SortField> sortFieldList, List<String> firstDataList, List<ScoreDoc> dataList) {
        Collections.sort(dataList, new MyDataComparator(sortFieldList, firstDataList, dataList));
    }
}
