CREATE EXTENSION IF NOT EXISTS unaccent;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE FUNCTION catalog_normalize(text) RETURNS text
  LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT
  AS $$ SELECT lower(public.unaccent('public.unaccent'::regdictionary, $1)) $$;

CREATE TABLE publishers
(
  id   UUID         PRIMARY KEY,
  name VARCHAR(200) NOT NULL
);

CREATE TABLE genres
(
  id   UUID         PRIMARY KEY,
  name VARCHAR(100) NOT NULL
);

CREATE TABLE authors
(
  id   UUID         PRIMARY KEY,
  name VARCHAR(200) NOT NULL
);

CREATE TABLE series
(
  id          UUID         PRIMARY KEY,
  name        VARCHAR(200) NOT NULL,
  description TEXT         NOT NULL
);

CREATE TABLE products
(
  id               UUID           PRIMARY KEY,
  type             VARCHAR(10)    NOT NULL CHECK (type IN ('MANGA', 'LIBRO', 'COMIC')),
  status           VARCHAR(12)    NOT NULL CHECK (status IN ('PUBLISHED', 'UNPUBLISHED')),
  title            VARCHAR(300)   NOT NULL,
  synopsis         TEXT           NOT NULL,
  price            NUMERIC(12, 0) NOT NULL CHECK (price > 0),
  isbn             VARCHAR(13) CHECK (isbn ~ '^([0-9]{9}[0-9X]|[0-9]{13})$'),
  language         VARCHAR(35)    NOT NULL,
  page_count       INTEGER        NOT NULL CHECK (page_count > 0),
  publication_date DATE           NOT NULL,
  cover_url        VARCHAR(2048)  NOT NULL,
  publisher_id     UUID           NOT NULL REFERENCES publishers (id),
  series_id        UUID REFERENCES series (id),
  volume_number    INTEGER CHECK (volume_number > 0),
  created_at       TIMESTAMPTZ    NOT NULL,
  CONSTRAINT products_series_volume_together_ck CHECK ((series_id IS NULL) = (volume_number IS NULL)),
  CONSTRAINT products_series_volume_uk UNIQUE (series_id, volume_number),
  CONSTRAINT products_isbn_uk UNIQUE (isbn)
);

CREATE TABLE product_authors
(
  product_id UUID NOT NULL REFERENCES products (id) ON DELETE CASCADE,
  author_id  UUID NOT NULL REFERENCES authors (id),
  PRIMARY KEY (product_id, author_id)
);

CREATE TABLE product_genres
(
  product_id UUID NOT NULL REFERENCES products (id) ON DELETE CASCADE,
  genre_id   UUID NOT NULL REFERENCES genres (id),
  PRIMARY KEY (product_id, genre_id)
);

CREATE INDEX products_pub_created_idx ON products (created_at, id) WHERE status = 'PUBLISHED';
CREATE INDEX products_pub_price_idx ON products (price, id) WHERE status = 'PUBLISHED';
CREATE INDEX products_pub_pubdate_idx ON products (publication_date, id) WHERE status = 'PUBLISHED';
CREATE INDEX products_pub_title_idx ON products (title, id) WHERE status = 'PUBLISHED';
CREATE INDEX products_pub_type_idx ON products (type) WHERE status = 'PUBLISHED';
CREATE INDEX products_pub_publisher_idx ON products (publisher_id) WHERE status = 'PUBLISHED';
CREATE INDEX products_title_trgm_idx ON products USING GIN (catalog_normalize(title) gin_trgm_ops);
CREATE INDEX authors_name_trgm_idx ON authors USING GIN (catalog_normalize(name) gin_trgm_ops);
CREATE INDEX product_authors_author_idx ON product_authors (author_id, product_id);
CREATE INDEX product_genres_genre_idx ON product_genres (genre_id, product_id);
