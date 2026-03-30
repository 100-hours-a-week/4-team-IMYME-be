-- ============================================================
-- 목적: search_vector에 knowledge_base.content를 포함시켜
--       FTS 브랜치가 지식 내용 기반 랭킹을 수행할 수 있도록 수정
-- 변경 전: tsvector(keyword.name) — 같은 keyword 내 모든 행 동일
-- 변경 후: tsvector(keyword.name, weight=A) || tsvector(content, weight=B)
-- 사용 모델: Qwen3-Embedding-0.6B (벡터 브랜치)
-- ============================================================

-- 1. 트리거 함수 교체
CREATE OR REPLACE FUNCTION update_kb_search_vector()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.keyword_id IS NOT NULL THEN
        -- keyword name(A): 도메인 연관성 신호 (높은 가중치)
        -- content(B): 지식 내용 관련도 신호 (낮은 가중치)
        NEW.search_vector :=
            setweight(
                COALESCE(
                    (SELECT to_tsvector('simple', name)
                     FROM keywords
                     WHERE id = NEW.keyword_id),
                    ''::tsvector
                ),
                'A'
            )
            ||
            setweight(to_tsvector('simple', NEW.content), 'B');
    ELSE
        -- keyword 없는 경우 content만으로 구성
        NEW.search_vector := setweight(to_tsvector('simple', NEW.content), 'B');
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 2. 기존 데이터 백필
-- keyword_id가 있는 행: keyword name(A) + content(B)
UPDATE knowledge_base kb
SET search_vector =
    setweight(
        COALESCE(
            (SELECT to_tsvector('simple', k.name)
             FROM keywords k
             WHERE k.id = kb.keyword_id),
            ''::tsvector
        ),
        'A'
    )
    ||
    setweight(to_tsvector('simple', kb.content), 'B')
WHERE kb.keyword_id IS NOT NULL;

-- keyword_id가 없는 행: content만
UPDATE knowledge_base kb
SET search_vector = setweight(to_tsvector('simple', kb.content), 'B')
WHERE kb.keyword_id IS NULL;
