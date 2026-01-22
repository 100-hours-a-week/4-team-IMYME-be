package com.imyme.mine.domain.keyword.controller;

import com.imyme.mine.domain.keyword.dto.CategoryWithKeywordsResponse;
import com.imyme.mine.domain.keyword.service.KeywordService;
import com.imyme.mine.global.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/keywords")
public class KeywordController {

    private final KeywordService keywordService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<CategoryWithKeywordsResponse>>> getAllKeywords(
            @RequestParam(required = false) Boolean isActive
    ) {
        List<CategoryWithKeywordsResponse> keywords = keywordService.getAllKeywordsGroupedByCategory(isActive);
        return ResponseEntity.ok(ApiResponse.success(keywords));
    }
}
