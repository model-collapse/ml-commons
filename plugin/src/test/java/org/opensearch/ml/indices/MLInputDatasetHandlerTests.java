/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 *
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 *
 */

package org.opensearch.ml.indices;

import org.apache.lucene.search.TotalHits;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.action.ActionListener;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.Client;
import org.opensearch.common.bytes.BytesArray;
import org.opensearch.common.bytes.BytesReference;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.dataframe.DataFrameBuilder;
import org.opensearch.ml.common.dataset.DataFrameInputDataset;
import org.opensearch.ml.common.dataset.SearchQueryInputDataset;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.search.builder.SearchSourceBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;


public class MLInputDatasetHandlerTests{
    Client client;
    MLInputDatasetHandler mlInputDatasetHandler;
    ActionListener<DataFrame> listener;
    DataFrame dataFrame;
    SearchResponse searchResponse;

    @Rule
    public ExpectedException expectedEx = ExpectedException.none();

    @Before
    public void setup() {
        Map<String, Object> source = new HashMap<>();
        source.put("taskId", "111");
        List<Map<String, Object>> mapList = new ArrayList<>();
        mapList.add(source);
        dataFrame = DataFrameBuilder.load(mapList);
        client = mock(Client.class);
        mlInputDatasetHandler = new MLInputDatasetHandler(client);
        listener = spy(new ActionListener<DataFrame>() {
            @Override
            public void onResponse(DataFrame dataFrame) {}

            @Override
            public void onFailure(Exception e) {}
        });

    }

    @Test
    public void testDataFrameInputDataset() {
        DataFrame testDataFrame = DataFrameBuilder.load(Collections.singletonList(new HashMap<String, Object>() {
            {
                put("key1", 2.0D);
            }
        }));
        DataFrameInputDataset dataFrameInputDataset = DataFrameInputDataset.builder()
                .dataFrame(testDataFrame)
                .build();
        DataFrame result = mlInputDatasetHandler.parseDataFrameInput(dataFrameInputDataset);
        Assert.assertEquals(testDataFrame, result);
    }

    @Test
    public void testDataFrameInputDatasetWrongType() {
        expectedEx.expect(IllegalArgumentException.class);
        expectedEx.expectMessage("Input dataset is not DATA_FRAME type.");
        SearchQueryInputDataset searchQueryInputDataset = SearchQueryInputDataset.builder()
                .indices(Arrays.asList("index1"))
                .searchSourceBuilder(new SearchSourceBuilder().query(QueryBuilders.matchAllQuery()))
                .build();
        DataFrame result = mlInputDatasetHandler.parseDataFrameInput(searchQueryInputDataset);
    }


    @Test
    @SuppressWarnings("unchecked")
    public void testSearchQueryInputDatasetWithHits() {
        searchResponse = mock(SearchResponse.class);
        BytesReference bytesArray = new BytesArray("{\"taskId\":\"111\"}");
        SearchHit hit = new SearchHit( 1 );
        hit.sourceRef(bytesArray);
        SearchHits hits = new SearchHits(new SearchHit[] {hit}, new TotalHits(1L, TotalHits.Relation.EQUAL_TO), 1f);
        when(searchResponse.getHits()).thenReturn(hits);
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = (ActionListener<SearchResponse>) invocation.getArguments() [1];
            listener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(), any());

        SearchQueryInputDataset searchQueryInputDataset = SearchQueryInputDataset.builder()
                .indices(Arrays.asList("index1"))
                .searchSourceBuilder(new SearchSourceBuilder().query(QueryBuilders.matchAllQuery()))
                .build();
        mlInputDatasetHandler.parseSearchQueryInput(searchQueryInputDataset, listener);
        ArgumentCaptor<DataFrame> captor = ArgumentCaptor.forClass(DataFrame.class);
        verify(listener, times(1)).onResponse(captor.capture());
        Assert.assertEquals(captor.getAllValues().size(), 1);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testSearchQueryInputDatasetWithoutHits() {
        searchResponse = mock(SearchResponse.class);
        SearchHits hits = new SearchHits(new SearchHit[0], new TotalHits(1L, TotalHits.Relation.EQUAL_TO), 1f);
        when(searchResponse.getHits()).thenReturn(hits);
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = (ActionListener<SearchResponse>) invocation.getArguments() [1];
            listener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(), any());

        SearchQueryInputDataset searchQueryInputDataset = SearchQueryInputDataset.builder()
                .indices(Arrays.asList("index1"))
                .searchSourceBuilder(new SearchSourceBuilder().query(QueryBuilders.matchAllQuery()))
                .build();
        mlInputDatasetHandler.parseSearchQueryInput(searchQueryInputDataset, listener);
        verify(listener, times(1)).onFailure(any());
    }

    @Test
    public void testSearchQueryInputDatasetWrongType() {
        expectedEx.expect(IllegalArgumentException.class);
        expectedEx.expectMessage("Input dataset is not SEARCH_QUERY type.");
        DataFrame testDataFrame = DataFrameBuilder.load(Collections.singletonList(new HashMap<String, Object>() {
            {
                put("key1", 2.0D);
            }
        }));
        DataFrameInputDataset dataFrameInputDataset = DataFrameInputDataset.builder()
                .dataFrame(testDataFrame)
                .build();
        mlInputDatasetHandler.parseSearchQueryInput(dataFrameInputDataset, listener);
    }

}
