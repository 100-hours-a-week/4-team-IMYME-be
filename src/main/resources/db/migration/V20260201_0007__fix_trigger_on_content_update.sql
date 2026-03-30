-- ============================================================
-- 목적: 트리거 발동 조건에 content 컬럼 추가
-- 변경 전: BEFORE INSERT OR UPDATE OF keyword_id
-- 변경 후: BEFORE INSERT OR UPDATE OF keyword_id, content
--
-- 배경:
--   V0006에서 search_vector가 tsvector(keyword.name[A]) || tsvector(content[B]) 구조로
--   변경되었으나, 트리거 발동 조건이 keyword_id만 감지하도록 그대로 남아있었다.
--   그 결과 KnowledgeBatchService.updateKnowledgeWithEvalResult()에서 content 컬럼만
--   변경되는 경우 트리거가 발동하지 않아 search_vector가 stale 상태로 유지되는
--   결함이 존재하였다.
--
-- 수정:
--   트리거를 재정의하여 content 변경 시에도 search_vector를 자동 재계산하도록 한다.
--   트리거 함수(update_kb_search_vector)는 V0006에서 이미 올바르게 수정되었으므로
--   함수 교체 없이 트리거 조건만 변경한다.
-- ============================================================

DROP TRIGGER IF EXISTS trg_update_kb_search_vector ON knowledge_base;

CREATE TRIGGER trg_update_kb_search_vector
BEFORE INSERT OR UPDATE OF keyword_id, content ON knowledge_base
FOR EACH ROW
EXECUTE FUNCTION update_kb_search_vector();
