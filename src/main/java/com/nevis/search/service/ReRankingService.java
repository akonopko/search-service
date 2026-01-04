package com.nevis.search.service;

import java.util.List;

public interface ReRankingService {

    List<DocumentSearchResult> reRank(String query, List<DocumentSearchResult> candidates);

}