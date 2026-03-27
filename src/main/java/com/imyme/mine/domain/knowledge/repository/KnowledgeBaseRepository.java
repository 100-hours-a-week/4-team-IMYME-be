package com.imyme.mine.domain.knowledge.repository;

import com.imyme.mine.domain.knowledge.entity.KnowledgeBase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 지식 베이스 리포지토리
 */
@Repository
public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBase, Long> {

  // 콘텐츠 해시 존재 여부 확인
  boolean existsByContentHash(String contentHash);

  // 콘텐츠 해시로 지식 조회
  Optional<KnowledgeBase> findByContentHash(String contentHash);

  // 임베딩 미생성 활성 지식 조회
  @Query("SELECT kb FROM KnowledgeBase kb WHERE kb.isActive = true AND kb.embedding IS NULL")
  List<KnowledgeBase> findAllWithoutEmbedding();

  // 키워드 ID로 활성 지식 조회
  @Query("SELECT kb FROM KnowledgeBase kb WHERE kb.keyword.id = :keywordId AND kb.isActive = true")
  List<KnowledgeBase> findByKeywordId(@Param("keywordId") Long keywordId);

  /**
   * 전체 벡터 유사도 검색 (Interface Projection 방식)
   *
   * <h3>사용 시나리오</h3>
   * <ul>
   * <li>일반 질의응답: 사용자 질문과 유사한 지식 검색</li>
   * <li>문서 요약: 관련 있는 지식들을 모아서 요약</li>
   * </ul>
   *
   * <h3>pgvector 연산자 설명</h3>
   * <ul>
   * <li><b>{@code <=>}</b>: 코사인 거리 연산자 (Cosine Distance)</li>
   * <li><b>{@code CAST(:param AS vector)}</b>: 문자열 파라미터를 PostgreSQL vector 타입으로
   * 변환</li>
   * <li>거리 값이 0에 가까울수록 유사 (0 = 동일, 2 = 정반대)</li>
   * </ul>
   *
   * <h3>HNSW 인덱스 활용</h3>
   * <p>
   * 이 쿼리는 {@code idx_kb_embedding} HNSW 인덱스를 자동으로 사용하여 빠른 근사 검색을 수행합니다.
   * </p>
   * <p>
   * 마이그레이션 파일(V20260129_0003)에서 생성된 인덱스 설정:
   * </p>
   * <ul>
   * <li>m=16: 그래프 연결 수 (메모리 vs 정확도 트레이드오프)</li>
   * <li>ef_construction=64: 인덱스 구축 시 탐색 범위 (높을수록 정확하지만 느림)</li>
   * </ul>
   *
   * @param queryEmbedding OpenAI API로부터 받은 1024차원 벡터를 문자열로 변환한 값
   *                       형식: "[0.1, -0.2, 0.5, ...]" (Service 계층에서 변환 필요)
   * @param limit          반환할 최대 결과 수 (Top-K)
   * @return 유사도 높은 순으로 정렬된 지식 목록 (거리 정보 포함)
   * @see KnowledgeSearchResult
   */
  @Query(value = """
      SELECT kb.id AS id,
             kb.keyword_id AS keywordId,
             kb.content AS content,
             kb.embedding AS embedding,
             kb.content_hash AS contentHash,
             kb.is_active AS isActive,
             kb.created_at AS createdAt,
             kb.updated_at AS updatedAt,
             (kb.embedding <=> CAST(:queryEmbedding AS vector)) AS distance
      FROM knowledge_base kb
      WHERE kb.is_active = true
        AND kb.embedding IS NOT NULL
      ORDER BY distance ASC

      LIMIT :limit
      """, nativeQuery = true)
  List<KnowledgeSearchResult> findSimilarKnowledgeByVector(
      @Param("queryEmbedding") String queryEmbedding,
      @Param("limit") int limit);

  /**
   * 키워드별 벡터 유사도 검색 (Interface Projection 방식)
   *
   * <h3>사용 시나리오</h3>
   * <ul>
   * <li>키워드별 맞춤형 피드백 생성: 사용자가 학습 중인 특정 키워드에 대한 지식만 검색</li>
   * <li>카테고리 필터링: 특정 도메인(Java, Spring 등)에 한정된 검색</li>
   * </ul>
   *
   * <h3>전체 검색 대비 차이점</h3>
   * <p>
   * {@link #findSimilarKnowledgeByVector}는 전체 지식을 대상으로 검색하지만,
   * 이 메서드는 WHERE 절에 {@code kb.keyword_id = :keywordId} 조건을 추가하여
   * 특정 키워드에 연관된 지식만 검색합니다.
   * </p>
   *
   * <h3>성능 최적화</h3>
   * <p>
   * WHERE 절의 keyword_id 필터링은 HNSW 인덱스 검색 <b>이후</b>에 적용됩니다.
   * 키워드별 검색이 빈번하다면, 키워드별로 별도 테이블을 구성하거나
   * Partial Index 생성을 고려할 수 있습니다.
   * </p>
   *
   * @param queryEmbedding OpenAI API로부터 받은 1024차원 벡터 문자열
   * @param keywordId      필터링할 키워드 ID
   * @param limit          반환할 최대 결과 수
   * @return 해당 키워드에 속한 지식 중 유사도 높은 순으로 정렬된 목록
   * @see KnowledgeSearchResult
   */
  @Query(value = """
      SELECT kb.id AS id,
             kb.keyword_id AS keywordId,
             kb.content AS content,
             kb.embedding AS embedding,
             kb.content_hash AS contentHash,
             kb.is_active AS isActive,
             kb.created_at AS createdAt,
             kb.updated_at AS updatedAt,
             (kb.embedding <=> CAST(:queryEmbedding AS vector)) AS distance
      FROM knowledge_base kb
      WHERE kb.is_active = true
        AND kb.embedding IS NOT NULL
        AND kb.keyword_id = :keywordId
      ORDER BY distance ASC
      LIMIT :limit
      """, nativeQuery = true)
  List<KnowledgeSearchResult> findSimilarKnowledgeByKeyword(
      @Param("queryEmbedding") String queryEmbedding,
      @Param("keywordId") Long keywordId,
      @Param("limit") int limit);

  /**
   * 하이브리드 RRF 검색 (Keyword FTS + Vector Semantic Search)
   *
   * <h3>사용 시나리오</h3>
   * <ul>
   * <li>RAG 시스템의 핵심 검색 로직: 키워드 매칭과 의미 기반 검색을 결합</li>
   * <li>중복 지식 판단: 신규 후보와 기존 지식의 유사도를 정확히 평가</li>
   * </ul>
   *
   * <h3>Hybrid RRF (Reciprocal Rank Fusion) 알고리즘</h3>
   * <p>
   * 두 가지 독립적인 검색 결과를 융합하는 알고리즘:
   * </p>
   * <ul>
   * <li><b>Keyword Search</b>: PostgreSQL Full-Text Search (ts_rank +
   * tsvector)</li>
   * <li><b>Semantic Search</b>: pgvector 코사인 유사도 (embedding &lt;=&gt;)</li>
   * <li><b>RRF Score</b>: 1/(k + rank) 공식으로 두 순위를 합산 (k=60, 표준값)</li>
   * </ul>
   *
   * <h3>성능 최적화</h3>
   * <ul>
   * <li>GIN 인덱스 (search_vector): 키워드 검색 가속화</li>
   * <li>HNSW 인덱스 (embedding): 벡터 검색 가속화</li>
   * <li>각 CTE는 Top-20만 추출하여 최종 RRF 계산 부하 최소화</li>
   * </ul>
   *
   * @param queryText      검색 텍스트 (키워드 추출용)
   * @param queryEmbedding 검색 벡터 (1024차원, "[0.1, -0.2, ...]" 형식)
   * @param keywordId      필터링할 키워드 ID
   * @param limit          반환할 최대 결과 수
   * @return RRF 점수 기준 정렬된 지식 목록 (유사도 포함)
   */
  @Query(value = """
      WITH keyword_results AS (
          -- [1단계 Pre-Fusion] 키워드 브랜치: FTS ts_rank 기준 상위 20개
          SELECT
              id,
              ts_rank(search_vector, websearch_to_tsquery('simple', :queryText)) AS rank
          FROM knowledge_base
          WHERE search_vector @@ websearch_to_tsquery('simple', :queryText)
            AND is_active = true
            AND keyword_id = :keywordId
          ORDER BY rank DESC
          LIMIT 20
      ),
      semantic_results AS (
          -- [1단계 Pre-Fusion] 시맨틱 브랜치: 코사인 거리 <= 0.3 필터 적용 후 상위 20개
          -- 문맥상 전혀 엉뚱한 문서가 RRF 병합 단계로 넘어오는 것을 여기서 차단
          SELECT
              id,
              1 - (embedding <=> CAST(:queryEmbedding AS vector)) AS similarity
          FROM knowledge_base
          WHERE embedding IS NOT NULL
            AND is_active = true
            AND keyword_id = :keywordId
            AND (embedding <=> CAST(:queryEmbedding AS vector)) <= 0.3
          ORDER BY embedding <=> CAST(:queryEmbedding AS vector)
          LIMIT 20
      ),
      keyword_ranked AS (
          SELECT id, ROW_NUMBER() OVER (ORDER BY rank DESC) AS rank_num
          FROM keyword_results
      ),
      semantic_ranked AS (
          SELECT
              sr.id,
              ROW_NUMBER() OVER (ORDER BY sr.similarity DESC) AS rank_num,
              sr.similarity
          FROM semantic_results sr
      )
      -- [3단계 Post-Fusion] RRF 점수 기준 정렬 후 Top-N cut-off만 수행
      -- 코사인 거리 등 다른 점수로 재필터링 금지
      SELECT
          kb.id AS id,
          kb.keyword_id AS keywordId,
          k.name AS keywordName,
          kb.content AS content,
          kb.embedding AS embedding,
          kb.content_hash AS contentHash,
          kb.is_active AS isActive,
          kb.created_at AS createdAt,
          kb.updated_at AS updatedAt,
          COALESCE(sr.similarity, 0.0) AS distance
      FROM knowledge_base kb
      JOIN keywords k ON kb.keyword_id = k.id
      LEFT JOIN keyword_ranked kr ON kb.id = kr.id
      LEFT JOIN semantic_ranked sr ON kb.id = sr.id
      WHERE (kr.id IS NOT NULL OR sr.id IS NOT NULL)
        AND kb.is_active = true
      ORDER BY
          -- [2단계 RRF] 차집합(한쪽에만 있는 문서)은 R_missing=1000 페널티 적용
          -- COALESCE(0) 대신 1.0/1060.0 으로 아주 작은 기여만 허용
          (0.65 * COALESCE(1.0/(60 + kr.rank_num), 1.0/1060.0))
          + (0.35 * COALESCE(1.0/(60 + sr.rank_num), 1.0/1060.0)) DESC
      LIMIT :limit
      """, nativeQuery = true)
  List<KnowledgeSearchResult> findSimilarKnowledgeByHybridRRF(
      @Param("queryText") String queryText,
      @Param("queryEmbedding") String queryEmbedding,
      @Param("keywordId") Long keywordId,
      @Param("limit") int limit);

  /**
   * 모든 활성 지식 조회 (최신순)
   * - 목적: 관리자 페이지에서 지식 목록 표시
   */
  @Query("SELECT kb FROM KnowledgeBase kb WHERE kb.isActive = true ORDER BY kb.createdAt DESC")
  List<KnowledgeBase> findAllActiveOrderByCreatedAtDesc();

  /**
   * 활성 지식 개수 조회
   * - 목적: 대시보드 통계
   */
  @Query("SELECT COUNT(kb) FROM KnowledgeBase kb WHERE kb.isActive = true")
  long countActiveKnowledge();

  /**
   * 임베딩 미생성 지식 개수 조회
   * - 목적: 배치 작업 모니터링
   */
  @Query("SELECT COUNT(kb) FROM KnowledgeBase kb WHERE kb.isActive = true AND kb.embedding IS NULL")
  long countWithoutEmbedding();
}
