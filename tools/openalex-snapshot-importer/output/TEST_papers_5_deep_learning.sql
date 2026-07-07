-- ═══════════════════════════════════════════════════════════════
-- SCITRACK — TEST IMPORT FILE
-- Generated: 2026-07-06T02:32:24.474Z
-- Papers: 5 (TEST — small sample from OpenAlex API)
--
-- 🧪 This is a TEST file. Run it to verify the SQL import pipeline
--    works correctly before downloading the full 100GB snapshot.
-- ═══════════════════════════════════════════════════════════════

SET NOCOUNT ON;
GO

-- Ensure API_SOURCE 'OpenAlex' exists
IF NOT EXISTS (SELECT 1 FROM API_SOURCE WHERE SourceName = 'OpenAlex')
BEGIN
    INSERT INTO API_SOURCE (SourceID, SourceName, BaseURL, IsActive, RateLimitRPM)
    VALUES (NEWID(), N'OpenAlex', N'https://api.openalex.org', 1, 100);
END
GO

-- ───────────────────────────────────────────────────────────────
-- TEST PAPER [1/5] Learning Multiple Layers of Features from Tiny Images
-- OpenAlex: https://openalex.org/W3118608800
-- Year: 2024 | Citations: 25499
-- DOI: 10.57702/zp44cu3g | Type: dissertation
-- ───────────────────────────────────────────────────────────────

BEGIN TRY
  BEGIN TRANSACTION;
  DECLARE @SourceID UNIQUEIDENTIFIER = (SELECT SourceID FROM API_SOURCE WHERE SourceName = 'OpenAlex');
  DECLARE @PaperID UNIQUEIDENTIFIER = NEWID();
  DECLARE @JournalID UNIQUEIDENTIFIER = NULL;
  DECLARE @FieldID UNIQUEIDENTIFIER = NULL;

  IF NOT EXISTS (SELECT 1 FROM RESEARCH_FIELD WHERE FieldName = N'Computer Science')
  BEGIN
      SET @FieldID = NEWID();
      INSERT INTO RESEARCH_FIELD (FieldID, FieldName, IsTracked)
      VALUES (@FieldID, N'Computer Science', 1);
  END
  ELSE
      SET @FieldID = (SELECT TOP 1 FieldID FROM RESEARCH_FIELD WHERE FieldName = N'Computer Science');

  -- Check duplicate by DOI
  IF EXISTS (SELECT 1 FROM RESEARCH_PAPER WHERE DOI = N'10.57702/zp44cu3g')
      THROW 50000, 'SKIP_DOI: W3118608800', 1;

  INSERT INTO RESEARCH_PAPER (PaperID, SourceID, JournalID, FieldID, Title, Abstract, DOI, PubDate, PubYear, CitationCount, IsOpenAccess, PdfUrl)
  VALUES (
      @PaperID, @SourceID, @JournalID, @FieldID,
      N'Learning Multiple Layers of Features from Tiny Images',
      N'April 8, 2009Groups at MIT and NYU have collected a dataset of millions of tiny colour images from the web. It is, in principle, an excellent dataset for unsupervised training of deep generative models, but previous researchers who have tried this have found it di cult to learn a good set of lters from the images. We show how to train a multi-layer generative model that learns to extract meaningful features which resemble those found in the human visual cortex. Using a novel parallelization algorithm to distribute the work among multiple machines connected on a network, we show how training such a model can be done in reasonable time. A second problematic aspect of the tiny images dataset is that there are no reliable class labels which makes it hard to use for object recognition experiments. We created two sets of reliable labels. The CIFAR-10 set has 6000 examples of each of 10 classes and the CIFAR-100 set has 600 examples of each of 100 non-overlapping classes. Using these labels, we show that object recognition is signi cantly',
      N'10.57702/zp44cu3g',
      '2024-01-01',
      2024,
      25499,
      1,
      N'https://doi.org/10.57702/zp44cu3g'
  );

  -- Authors (1)
  IF NOT EXISTS (SELECT 1 FROM AUTHOR WHERE SourceID = @SourceID AND ExternalAuthorID = N'A5031152245')
  BEGIN
      DECLARE @AID_1 UNIQUEIDENTIFIER = NEWID();
      INSERT INTO AUTHOR (AuthorID, SourceID, ExternalAuthorID, FullName, Affiliation, HIndex, TotalCitations)
      VALUES (@AID_1, @SourceID, N'A5031152245', N'Alex Krizhevsky', N'University of Toronto', 0, 0);
  END
  ELSE
      DECLARE @AID_1 UNIQUEIDENTIFIER = (SELECT AuthorID FROM AUTHOR WHERE SourceID = @SourceID AND ExternalAuthorID = N'A5031152245');

  IF NOT EXISTS (SELECT 1 FROM PAPER_AUTHOR WHERE PaperID = @PaperID AND AuthorID = @AID_1)
      INSERT INTO PAPER_AUTHOR (PaperID, AuthorID, AuthorOrder, IsCorresponding) VALUES (@PaperID, @AID_1, 1, 0);

  -- Keywords (8)
  DECLARE @KWID_0 UNIQUEIDENTIFIER;
  IF EXISTS (SELECT 1 FROM KEYWORD WHERE NormalizedText = N'boltzmann machine')
  BEGIN
      SET @KWID_0 = (SELECT TOP 1 KeywordID FROM KEYWORD WHERE NormalizedText = N'boltzmann machine');
      UPDATE KEYWORD SET PaperCount = ISNULL(PaperCount, 0) + 1 WHERE KeywordID = @KWID_0;
  END
  ELSE
  BEGIN
      SET @KWID_0 = NEWID();
      INSERT INTO KEYWORD (KeywordID, FieldID, KeywordText, NormalizedText, PaperCount) VALUES (@KWID_0, @FieldID, N'Boltzmann machine', N'boltzmann machine', 1);
  END
  IF NOT EXISTS (SELECT 1 FROM PAPER_KEYWORD WHERE PaperID = @PaperID AND KeywordID = @KWID_0)
      INSERT INTO PAPER_KEYWORD (PaperID, KeywordID, RelevanceScore) VALUES (@PaperID, @KWID_0, 0.7699);

  DECLARE @KWID_1 UNIQUEIDENTIFIER;
  IF EXISTS (SELECT 1 FROM KEYWORD WHERE NormalizedText = N'artificial intelligence')
  BEGIN
      SET @KWID_1 = (SELECT TOP 1 KeywordID FROM KEYWORD WHERE NormalizedText = N'artificial intelligence');
      UPDATE KEYWORD SET PaperCount = ISNULL(PaperCount, 0) + 1 WHERE KeywordID = @KWID_1;
  END
  ELSE
  BEGIN
      SET @KWID_1 = NEWID();
      INSERT INTO KEYWORD (KeywordID, FieldID, KeywordText, NormalizedText, PaperCount) VALUES (@KWID_1, @FieldID, N'Artificial intelligence', N'artificial intelligence', 1);
  END
  IF NOT EXISTS (SELECT 1 FROM PAPER_KEYWORD WHERE PaperID = @PaperID AND KeywordID = @KWID_1)
      INSERT INTO PAPER_KEYWORD (PaperID, KeywordID, RelevanceScore) VALUES (@PaperID, @KWID_1, 0.7328);

  DECLARE @KWID_2 UNIQUEIDENTIFIER;
  IF EXISTS (SELECT 1 FROM KEYWORD WHERE NormalizedText = N'classifier (uml)')
  BEGIN
      SET @KWID_2 = (SELECT TOP 1 KeywordID FROM KEYWORD WHERE NormalizedText = N'classifier (uml)');
      UPDATE KEYWORD SET PaperCount = ISNULL(PaperCount, 0) + 1 WHERE KeywordID = @KWID_2;
  END
  ELSE
  BEGIN
      SET @KWID_2 = NEWID();
      INSERT INTO KEYWORD (KeywordID, FieldID, KeywordText, NormalizedText, PaperCount) VALUES (@KWID_2, @FieldID, N'Classifier (UML)', N'classifier (uml)', 1);
  END
  IF NOT EXISTS (SELECT 1 FROM PAPER_KEYWORD WHERE PaperID = @PaperID AND KeywordID = @KWID_2)
      INSERT INTO PAPER_KEYWORD (PaperID, KeywordID, RelevanceScore) VALUES (@PaperID, @KWID_2, 0.7270);

  DECLARE @KWID_3 UNIQUEIDENTIFIER;
  IF EXISTS (SELECT 1 FROM KEYWORD WHERE NormalizedText = N'computer science')
  BEGIN
      SET @KWID_3 = (SELECT TOP 1 KeywordID FROM KEYWORD WHERE NormalizedText = N'computer science');
      UPDATE KEYWORD SET PaperCount = ISNULL(PaperCount, 0) + 1 WHERE KeywordID = @KWID_3;
  END
  ELSE
  BEGIN
      SET @KWID_3 = NEWID();
      INSERT INTO KEYWORD (KeywordID, FieldID, KeywordText, NormalizedText, PaperCount) VALUES (@KWID_3, @FieldID, N'Computer science', N'computer science', 1);
  END
  IF NOT EXISTS (SELECT 1 FROM PAPER_KEYWORD WHERE PaperID = @PaperID AND KeywordID = @KWID_3)
      INSERT INTO PAPER_KEYWORD (PaperID, KeywordID, RelevanceScore) VALUES (@PaperID, @KWID_3, 0.6687);

  DECLARE @KWID_4 UNIQUEIDENTIFIER;
  IF EXISTS (SELECT 1 FROM KEYWORD WHERE NormalizedText = N'generative grammar')
  BEGIN
      SET @KWID_4 = (SELECT TOP 1 KeywordID FROM KEYWORD WHERE NormalizedText = N'generative grammar');
      UPDATE KEYWORD SET PaperCount = ISNULL(PaperCount, 0) + 1 WHERE KeywordID = @KWID_4;
  END
  ELSE
  BEGIN
      SET @KWID_4 = NEWID();
      INSERT INTO KEYWORD (KeywordID, FieldID, KeywordText, NormalizedText, PaperCount) VALUES (@KWID_4, @FieldID, N'Generative grammar', N'generative grammar', 1);
  END
  IF NOT EXISTS (SELECT 1 FROM PAPER_KEYWORD WHERE PaperID = @PaperID AND KeywordID = @KWID_4)
      INSERT INTO PAPER_KEYWORD (PaperID, KeywordID, RelevanceScore) VALUES (@PaperID, @KWID_4, 0.6336);

  DECLARE @KWID_5 UNIQUEIDENTIFIER;
  IF EXISTS (SELECT 1 FROM KEYWORD WHERE NormalizedText = N'deep learning')
  BEGIN
      SET @KWID_5 = (SELECT TOP 1 KeywordID FROM KEYWORD WHERE NormalizedText = N'deep learning');
      UPDATE KEYWORD SET PaperCount = ISNULL(PaperCount, 0) + 1 WHERE KeywordID = @KWID_5;
  END
  ELSE
  BEGIN
      SET @KWID_5 = NEWID();
      INSERT INTO KEYWORD (KeywordID, FieldID, KeywordText, NormalizedText, PaperCount) VALUES (@KWID_5, @FieldID, N'Deep learning', N'deep learning', 1);
  END
  IF NOT EXISTS (SELECT 1 FROM PAPER_KEYWORD WHERE PaperID = @PaperID AND KeywordID = @KWID_5)
      INSERT INTO PAPER_KEYWORD (PaperID, KeywordID, RelevanceScore) VALUES (@PaperID, @KWID_5, 0.6333);

  DECLARE @KWID_6 UNIQUEIDENTIFIER;
  IF EXISTS (SELECT 1 FROM KEYWORD WHERE NormalizedText = N'pattern recognition (psychology)')
  BEGIN
      SET @KWID_6 = (SELECT TOP 1 KeywordID FROM KEYWORD WHERE NormalizedText = N'pattern recognition (psychology)');
      UPDATE KEYWORD SET PaperCount = ISNULL(PaperCount, 0) + 1 WHERE KeywordID = @KWID_6;
  END
  ELSE
  BEGIN
      SET @KWID_6 = NEWID();
      INSERT INTO KEYWORD (KeywordID, FieldID, KeywordText, NormalizedText, PaperCount) VALUES (@KWID_6, @FieldID, N'Pattern recognition (psychology)', N'pattern recognition (psychology)', 1);
  END
  IF NOT EXISTS (SELECT 1 FROM PAPER_KEYWORD WHERE PaperID = @PaperID AND KeywordID = @KWID_6)
      INSERT INTO PAPER_KEYWORD (PaperID, KeywordID, RelevanceScore) VALUES (@PaperID, @KWID_6, 0.5351);

  DECLARE @KWID_7 UNIQUEIDENTIFIER;
  IF EXISTS (SELECT 1 FROM KEYWORD WHERE NormalizedText = N'pixel')
  BEGIN
      SET @KWID_7 = (SELECT TOP 1 KeywordID FROM KEYWORD WHERE NormalizedText = N'pixel');
      UPDATE KEYWORD SET PaperCount = ISNULL(PaperCount, 0) + 1 WHERE KeywordID = @KWID_7;
  END
  ELSE
  BEGIN
      SET @KWID_7 = NEWID();
      INSERT INTO KEYWORD (KeywordID, FieldID, KeywordText, NormalizedText, PaperCount) VALUES (@KWID_7, @FieldID, N'Pixel', N'pixel', 1);
  END
  IF NOT EXISTS (SELECT 1 FROM PAPER_KEYWORD WHERE PaperID = @PaperID AND KeywordID = @KWID_7)
      INSERT INTO PAPER_KEYWORD (PaperID, KeywordID, RelevanceScore) VALUES (@PaperID, @KWID_7, 0.5327);

  COMMIT TRANSACTION;
  PRINT '✅ [1/5] OK: W3118608800 — Learning Multiple Layers of Features from Tiny Ima';
END TRY
BEGIN CATCH
  IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
  DECLARE @ErrMsg NVARCHAR(MAX) = ERROR_MESSAGE();
  IF @ErrMsg LIKE '%SKIP_DOI%' OR @ErrMsg LIKE '%SKIP_TITLE%'
      PRINT '⏭ [1/5] SKIP: W3118608800 — duplicate (already in DB)';
  ELSE
  BEGIN
      PRINT '❌ [1/5] ERROR: W3118608800 — ' + @ErrMsg + ' (Line: ' + CAST(ERROR_LINE() AS NVARCHAR) + ')';
      THROW;
  END
END CATCH
GO

-- ───────────────────────────────────────────────────────────────
-- TEST PAPER [2/5] Two new Later Stone Age sites from the Final Pleistocene in the Falémé
-- OpenAlex: https://openalex.org/W4392145873
-- Year: 2024 | Citations: 23842
-- DOI: 10.3929/ethz-b-000667478 | Type: preprint
-- ───────────────────────────────────────────────────────────────

BEGIN TRY
  BEGIN TRANSACTION;
  DECLARE @SourceID UNIQUEIDENTIFIER = (SELECT SourceID FROM API_SOURCE WHERE SourceName = 'OpenAlex');
  DECLARE @PaperID UNIQUEIDENTIFIER = NEWID();
  DECLARE @JournalID UNIQUEIDENTIFIER = NULL;
  DECLARE @FieldID UNIQUEIDENTIFIER = NULL;

  IF NOT EXISTS (SELECT 1 FROM JOURNAL WHERE ISSN = N'2622-8912')
  BEGIN
      SET @JournalID = NEWID();
      INSERT INTO JOURNAL (JournalID, SourceID, JournalName, ISSN, Publisher, IsActive)
      VALUES (@JournalID, @SourceID, N'ENLIGHTEN (Jurnal Bimbingan dan Konseling Islam)', N'2622-8912', NULL, 1);
  END
  ELSE
      SET @JournalID = (SELECT TOP 1 JournalID FROM JOURNAL WHERE ISSN = N'2622-8912');

  IF NOT EXISTS (SELECT 1 FROM RESEARCH_FIELD WHERE FieldName = N'Medicine')
  BEGIN
      SET @FieldID = NEWID();
      INSERT INTO RESEARCH_FIELD (FieldID, FieldName, IsTracked)
      VALUES (@FieldID, N'Medicine', 1);
  END
  ELSE
      SET @FieldID = (SELECT TOP 1 FieldID FROM RESEARCH_FIELD WHERE FieldName = N'Medicine');

  -- Check duplicate by DOI
  IF EXISTS (SELECT 1 FROM RESEARCH_PAPER WHERE DOI = N'10.3929/ethz-b-000667478')
      THROW 50000, 'SKIP_DOI: W4392145873', 1;

  INSERT INTO RESEARCH_PAPER (PaperID, SourceID, JournalID, FieldID, Title, Abstract, DOI, PubDate, PubYear, CitationCount, IsOpenAccess, PdfUrl)
  VALUES (
      @PaperID, @SourceID, @JournalID, @FieldID,
      N'Two new Later Stone Age sites from the Final Pleistocene in the Falémé Valley, eastern Senegal',
      N'30 pages, 8 figures, 1 table, supporting information https://doi.org/10.1371/journal.pone.0308461.-- Data Availability: The data underlying the results presented in the study are available through ONC’s Oceans 3.0 data management system (https://data.oceannetworks.ca/home)',
      N'10.3929/ethz-b-000667478',
      '2024-03-28',
      2024,
      23842,
      1,
      N'https://eprints.gla.ac.uk/379161/3/379161.pdf'
  );

  -- Authors (5)
  DECLARE @AID_1 UNIQUEIDENTIFIER = NEWID();
  INSERT INTO AUTHOR (AuthorID, SourceID, ExternalAuthorID, FullName, Affiliation, HIndex, TotalCitations)
  VALUES (@AID_1, @SourceID, NULL, N'Ndiaye, Matar', N'NuMeCan - Nutrition, Métabolismes et Cancer (Campus Villejean (Bât 8) - CHU Rennes (Bât 15) - Université de Rennes 1, 2 Avenue du Professeur Léon Bernard, 35043 Rennes, France - France)', 0, 0);

  IF NOT EXISTS (SELECT 1 FROM PAPER_AUTHOR WHERE PaperID = @PaperID AND AuthorID = @AID_1)
      INSERT INTO PAPER_AUTHOR (PaperID, AuthorID, AuthorOrder, IsCorresponding) VALUES (@PaperID, @AID_1, 1, 0);

  DECLARE @AID_2 UNIQUEIDENTIFIER = NEWID();
  INSERT INTO AUTHOR (AuthorID, SourceID, ExternalAuthorID, FullName, Affiliation, HIndex, TotalCitations)
  VALUES (@AID_2, @SourceID, NULL, N'Lespez, Laurent', N'US 1395 ANI-SCAN [INRA] (Saint-Gilles - France)', 0, 0);

  IF NOT EXISTS (SELECT 1 FROM PAPER_AUTHOR WHERE PaperID = @PaperID AND AuthorID = @AID_2)
      INSERT INTO PAPER_AUTHOR (PaperID, AuthorID, AuthorOrder, IsCorresponding) VALUES (@PaperID, @AID_2, 2, 0);

  DECLARE @AID_3 UNIQUEIDENTIFIER = NEWID();
  INSERT INTO AUTHOR (AuthorID, SourceID, ExternalAuthorID, FullName, Affiliation, HIndex, TotalCitations)
  VALUES (@AID_3, @SourceID, NULL, N'Tribolo, Chantal', N'NuMeCan - Nutrition, Métabolismes et Cancer (Campus Villejean (Bât 8) - CHU Rennes (Bât 15) - Université de Rennes 1, 2 Avenue du Professeur Léon Bernard, 35043 Rennes, France - France)', 0, 0);

  IF NOT EXISTS (SELECT 1 FROM PAPER_AUTHOR WHERE PaperID = @PaperID AND AuthorID = @AID_3)
      INSERT INTO PAPER_AUTHOR (PaperID, AuthorID, AuthorOrder, IsCorresponding) VALUES (@PaperID, @AID_3, 3, 0);

  DECLARE @AID_4 UNIQUEIDENTIFIER = NEWID();
  INSERT INTO AUTHOR (AuthorID, SourceID, ExternalAuthorID, FullName, Affiliation, HIndex, TotalCitations)
  VALUES (@AID_4, @SourceID, NULL, N'Rasse, Michel', N'NuMeCan - Nutrition, Métabolismes et Cancer (Campus Villejean (Bât 8) - CHU Rennes (Bât 15) - Université de Rennes 1, 2 Avenue du Professeur Léon Bernard, 35043 Rennes, France - France)', 0, 0);

  IF NOT EXISTS (SELECT 1 FROM PAPER_AUTHOR WHERE PaperID = @PaperID AND AuthorID = @AID_4)
      INSERT INTO PAPER_AUTHOR (PaperID, AuthorID, AuthorOrder, IsCorresponding) VALUES (@PaperID, @AID_4, 4, 0);

  DECLARE @AID_5 UNIQUEIDENTIFIER = NEWID();
  INSERT INTO AUTHOR (AuthorID, SourceID, ExternalAuthorID, FullName, Affiliation, HIndex, TotalCitations)
  VALUES (@AID_5, @SourceID, NULL, N'Hadjas, Irka', N'Unknown', 0, 0);

  IF NOT EXISTS (SELECT 1 FROM PAPER_AUTHOR WHERE PaperID = @PaperID AND AuthorID = @AID_5)
      INSERT INTO PAPER_AUTHOR (PaperID, AuthorID, AuthorOrder, IsCorresponding) VALUES (@PaperID, @AID_5, 5, 0);

  -- Keywords (8)
  DECLARE @KWID_0 UNIQUEIDENTIFIER;
  IF EXISTS (SELECT 1 FROM KEYWORD WHERE NormalizedText = N'insulin sensitivity')
  BEGIN
      SET @KWID_0 = (SELECT TOP 1 KeywordID FROM KEYWORD WHERE NormalizedText = N'insulin sensitivity');
      UPDATE KEYWORD SET PaperCount = ISNULL(PaperCount, 0) + 1 WHERE KeywordID = @KWID_0;
  END
  ELSE
  BEGIN
      SET @KWID_0 = NEWID();
      INSERT INTO KEYWORD (KeywordID, FieldID, KeywordText, NormalizedText, PaperCount) VALUES (@KWID_0, @FieldID, N'Insulin sensitivity', N'insulin sensitivity', 1);
  END
  IF NOT EXISTS (SELECT 1 FROM PAPER_KEYWORD WHERE PaperID = @PaperID AND KeywordID = @KWID_0)
      INSERT INTO PAPER_KEYWORD (PaperID, KeywordID, RelevanceScore) VALUES (@PaperID, @KWID_0, 0.7485);

  DECLARE @KWID_1 UNIQUEIDENTIFIER;
  IF EXISTS (SELECT 1 FROM KEYWORD WHERE NormalizedText = N'sugar')
  BEGIN
      SET @KWID_1 = (SELECT TOP 1 KeywordID FROM KEYWORD WHERE NormalizedText = N'sugar');
      UPDATE KEYWORD SET PaperCount = ISNULL(PaperCount, 0) + 1 WHERE KeywordID = @KWID_1;
  END
  ELSE
  BEGIN
      SET @KWID_1 = NEWID();
      INSERT INTO KEYWORD (KeywordID, FieldID, KeywordText, NormalizedText, PaperCount) VALUES (@KWID_1, @FieldID, N'Sugar', N'sugar', 1);
  END
  IF NOT EXISTS (SELECT 1 FROM PAPER_KEYWORD WHERE PaperID = @PaperID AND KeywordID = @KWID_1)
      INSERT INTO PAPER_KEYWORD (PaperID, KeywordID, RelevanceScore) VALUES (@PaperID, @KWID_1, 0.5784);

  DECLARE @KWID_2 UNIQUEIDENTIFIER;
  IF EXISTS (SELECT 1 FROM KEYWORD WHERE NormalizedText = N'insulin')
  BEGIN
      SET @KWID_2 = (SELECT TOP 1 KeywordID FROM KEYWORD WHERE NormalizedText = N'insulin');
      UPDATE KEYWORD SET PaperCount = ISNULL(PaperCount, 0) + 1 WHERE KeywordID = @KWID_2;
  END
  ELSE
  BEGIN
      SET @KWID_2 = NEWID();
      INSERT INTO KEYWORD (KeywordID, FieldID, KeywordText, NormalizedText, PaperCount) VALUES (@KWID_2, @FieldID, N'Insulin', N'insulin', 1);
  END
  IF NOT EXISTS (SELECT 1 FROM PAPER_KEYWORD WHERE PaperID = @PaperID AND KeywordID = @KWID_2)
      INSERT INTO PAPER_KEYWORD (PaperID, KeywordID, RelevanceScore) VALUES (@PaperID, @KWID_2, 0.5165);

  DECLARE @KWID_3 UNIQUEIDENTIFIER;
  IF EXISTS (SELECT 1 FROM KEYWORD WHERE NormalizedText = N'carbohydrate metabolism')
  BEGIN
      SET @KWID_3 = (SELECT TOP 1 KeywordID FROM KEYWORD WHERE NormalizedText = N'carbohydrate metabolism');
      UPDATE KEYWORD SET PaperCount = ISNULL(PaperCount, 0) + 1 WHERE KeywordID = @KWID_3;
  END
  ELSE
  BEGIN
      SET @KWID_3 = NEWID();
      INSERT INTO KEYWORD (KeywordID, FieldID, KeywordText, NormalizedText, PaperCount) VALUES (@KWID_3, @FieldID, N'Carbohydrate metabolism', N'carbohydrate metabolism', 1);
  END
  IF NOT EXISTS (SELECT 1 FROM PAPER_KEYWORD WHERE PaperID = @PaperID AND KeywordID = @KWID_3)
      INSERT INTO PAPER_KEYWORD (PaperID, KeywordID, RelevanceScore) VALUES (@PaperID, @KWID_3, 0.4987);

  DECLARE @KWID_4 UNIQUEIDENTIFIER;
  IF EXISTS (SELECT 1 FROM KEYWORD WHERE NormalizedText = N'metabolism')
  BEGIN
      SET @KWID_4 = (SELECT TOP 1 KeywordID FROM KEYWORD WHERE NormalizedText = N'metabolism');
      UPDATE KEYWORD SET PaperCount = ISNULL(PaperCount, 0) + 1 WHERE KeywordID = @KWID_4;
  END
  ELSE
  BEGIN
      SET @KWID_4 = NEWID();
      INSERT INTO KEYWORD (KeywordID, FieldID, KeywordText, NormalizedText, PaperCount) VALUES (@KWID_4, @FieldID, N'Metabolism', N'metabolism', 1);
  END
  IF NOT EXISTS (SELECT 1 FROM PAPER_KEYWORD WHERE PaperID = @PaperID AND KeywordID = @KWID_4)
      INSERT INTO PAPER_KEYWORD (PaperID, KeywordID, RelevanceScore) VALUES (@PaperID, @KWID_4, 0.4801);

  DECLARE @KWID_5 UNIQUEIDENTIFIER;
  IF EXISTS (SELECT 1 FROM KEYWORD WHERE NormalizedText = N'blood sugar')
  BEGIN
      SET @KWID_5 = (SELECT TOP 1 KeywordID FROM KEYWORD WHERE NormalizedText = N'blood sugar');
      UPDATE KEYWORD SET PaperCount = ISNULL(PaperCount, 0) + 1 WHERE KeywordID = @KWID_5;
  END
  ELSE
  BEGIN
      SET @KWID_5 = NEWID();
      INSERT INTO KEYWORD (KeywordID, FieldID, KeywordText, NormalizedText, PaperCount) VALUES (@KWID_5, @FieldID, N'Blood sugar', N'blood sugar', 1);
  END
  IF NOT EXISTS (SELECT 1 FROM PAPER_KEYWORD WHERE PaperID = @PaperID AND KeywordID = @KWID_5)
      INSERT INTO PAPER_KEYWORD (PaperID, KeywordID, RelevanceScore) VALUES (@PaperID, @KWID_5, 0.4527);

  DECLARE @KWID_6 UNIQUEIDENTIFIER;
  IF EXISTS (SELECT 1 FROM KEYWORD WHERE NormalizedText = N'consumption (sociology)')
  BEGIN
      SET @KWID_6 = (SELECT TOP 1 KeywordID FROM KEYWORD WHERE NormalizedText = N'consumption (sociology)');
      UPDATE KEYWORD SET PaperCount = ISNULL(PaperCount, 0) + 1 WHERE KeywordID = @KWID_6;
  END
  ELSE
  BEGIN
      SET @KWID_6 = NEWID();
      INSERT INTO KEYWORD (KeywordID, FieldID, KeywordText, NormalizedText, PaperCount) VALUES (@KWID_6, @FieldID, N'Consumption (sociology)', N'consumption (sociology)', 1);
  END
  IF NOT EXISTS (SELECT 1 FROM PAPER_KEYWORD WHERE PaperID = @PaperID AND KeywordID = @KWID_6)
      INSERT INTO PAPER_KEYWORD (PaperID, KeywordID, RelevanceScore) VALUES (@PaperID, @KWID_6, 0.4380);

  DECLARE @KWID_7 UNIQUEIDENTIFIER;
  IF EXISTS (SELECT 1 FROM KEYWORD WHERE NormalizedText = N'endocrinology')
  BEGIN
      SET @KWID_7 = (SELECT TOP 1 KeywordID FROM KEYWORD WHERE NormalizedText = N'endocrinology');
      UPDATE KEYWORD SET PaperCount = ISNULL(PaperCount, 0) + 1 WHERE KeywordID = @KWID_7;
  END
  ELSE
  BEGIN
      SET @KWID_7 = NEWID();
      INSERT INTO KEYWORD (KeywordID, FieldID, KeywordText, NormalizedText, PaperCount) VALUES (@KWID_7, @FieldID, N'Endocrinology', N'endocrinology', 1);
  END
  IF NOT EXISTS (SELECT 1 FROM PAPER_KEYWORD WHERE PaperID = @PaperID AND KeywordID = @KWID_7)
      INSERT INTO PAPER_KEYWORD (PaperID, KeywordID, RelevanceScore) VALUES (@PaperID, @KWID_7, 0.3983);

  COMMIT TRANSACTION;
  PRINT '✅ [2/5] OK: W4392145873 — Two new Later Stone Age sites from the Final Pleis';
END TRY
BEGIN CATCH
  IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
  DECLARE @ErrMsg NVARCHAR(MAX) = ERROR_MESSAGE();
  IF @ErrMsg LIKE '%SKIP_DOI%' OR @ErrMsg LIKE '%SKIP_TITLE%'
      PRINT '⏭ [2/5] SKIP: W4392145873 — duplicate (already in DB)';
  ELSE
  BEGIN
      PRINT '❌ [2/5] ERROR: W4392145873 — ' + @ErrMsg + ' (Line: ' + CAST(ERROR_LINE() AS NVARCHAR) + ')';
      THROW;
  END
END CATCH
GO

-- ───────────────────────────────────────────────────────────────
-- TEST PAPER [3/5] Batch Normalization: Accelerating Deep Network Training by Reducing In
-- OpenAlex: https://openalex.org/W2949117887
-- Year: 2024 | Citations: 15692
-- DOI: 10.57702/o9raffed | Type: preprint
-- ───────────────────────────────────────────────────────────────

BEGIN TRY
  BEGIN TRANSACTION;
  DECLARE @SourceID UNIQUEIDENTIFIER = (SELECT SourceID FROM API_SOURCE WHERE SourceName = 'OpenAlex');
  DECLARE @PaperID UNIQUEIDENTIFIER = NEWID();
  DECLARE @JournalID UNIQUEIDENTIFIER = NULL;
  DECLARE @FieldID UNIQUEIDENTIFIER = NULL;

  -- No ISSN for journal: arXiv (Cornell University)
  IF NOT EXISTS (SELECT 1 FROM JOURNAL WHERE JournalName = N'arXiv (Cornell University)')
  BEGIN
      SET @JournalID = NEWID();
      INSERT INTO JOURNAL (JournalID, SourceID, JournalName, ISSN, Publisher, IsActive)
      VALUES (@JournalID, @SourceID, N'arXiv (Cornell University)', NULL, N'Cornell University', 1);
  END
  ELSE
      SET @JournalID = (SELECT TOP 1 JournalID FROM JOURNAL WHERE JournalName = N'arXiv (Cornell University)');

  IF NOT EXISTS (SELECT 1 FROM RESEARCH_FIELD WHERE FieldName = N'Computer Science')
  BEGIN
      SET @FieldID = NEWID();
      INSERT INTO RESEARCH_FIELD (FieldID, FieldName, IsTracked)
      VALUES (@FieldID, N'Computer Science', 1);
  END
  ELSE
      SET @FieldID = (SELECT TOP 1 FieldID FROM RESEARCH_FIELD WHERE FieldName = N'Computer Science');

  -- Check duplicate by DOI
  IF EXISTS (SELECT 1 FROM RESEARCH_PAPER WHERE DOI = N'10.57702/o9raffed')
      THROW 50000, 'SKIP_DOI: W2949117887', 1;

  INSERT INTO RESEARCH_PAPER (PaperID, SourceID, JournalID, FieldID, Title, Abstract, DOI, PubDate, PubYear, CitationCount, IsOpenAccess, PdfUrl)
  VALUES (
      @PaperID, @SourceID, @JournalID, @FieldID,
      N'Batch Normalization: Accelerating Deep Network Training by Reducing Internal Covariate Shift',
      N'Training Deep Neural Networks is complicated by the fact that the distribution of each layer''s inputs changes during training, as the parameters of the previous layers change. This slows down the training by requiring lower learning rates and careful parameter initialization, and makes it notoriously hard to train models with saturating nonlinearities. We refer to this phenomenon as internal covariate shift, and address the problem by normalizing layer inputs. Our method draws its strength from making normalization a part of the model architecture and performing the normalization for each training mini-batch. Batch Normalization allows us to use much higher learning rates and be less careful about initialization. It also acts as a regularizer, in some cases eliminating the need for Dropout. Applied to a state-of-the-art image classification model, Batch Normalization achieves the same accuracy with 14 times fewer training steps, and beats the original model by a significant margin. Using an ensemble of batch-normalized networks, we improve upon the best published result on ImageNet classification: reaching 4.9% top-5 validation error (and 4.8% test error), exceeding the accuracy of human raters.',
      N'10.57702/o9raffed',
      '2024-01-01',
      2024,
      15692,
      1,
      N'http://export.arxiv.org/pdf/1502.03167'
  );

  -- Authors (1)
  IF NOT EXISTS (SELECT 1 FROM AUTHOR WHERE SourceID = @SourceID AND ExternalAuthorID = N'A5032800189')
  BEGIN
      DECLARE @AID_1 UNIQUEIDENTIFIER = NEWID();
      INSERT INTO AUTHOR (AuthorID, SourceID, ExternalAuthorID, FullName, Affiliation, HIndex, TotalCitations)
      VALUES (@AID_1, @SourceID, N'A5032800189', N'Sergey Ioffe', N'Google, 1600 Amphitheatre Pkwy, Mountain View, CA 94043', 0, 0);
  END
  ELSE
      DECLARE @AID_1 UNIQUEIDENTIFIER = (SELECT AuthorID FROM AUTHOR WHERE SourceID = @SourceID AND ExternalAuthorID = N'A5032800189');

  IF NOT EXISTS (SELECT 1 FROM PAPER_AUTHOR WHERE PaperID = @PaperID AND AuthorID = @AID_1)
      INSERT INTO PAPER_AUTHOR (PaperID, AuthorID, AuthorOrder, IsCorresponding) VALUES (@PaperID, @AID_1, 1, 0);

  -- Keywords (8)
  DECLARE @KWID_0 UNIQUEIDENTIFIER;
  IF EXISTS (SELECT 1 FROM KEYWORD WHERE NormalizedText = N'normalization (sociology)')
  BEGIN
      SET @KWID_0 = (SELECT TOP 1 KeywordID FROM KEYWORD WHERE NormalizedText = N'normalization (sociology)');
      UPDATE KEYWORD SET PaperCount = ISNULL(PaperCount, 0) + 1 WHERE KeywordID = @KWID_0;
  END
  ELSE
  BEGIN
      SET @KWID_0 = NEWID();
      INSERT INTO KEYWORD (KeywordID, FieldID, KeywordText, NormalizedText, PaperCount) VALUES (@KWID_0, @FieldID, N'Normalization (sociology)', N'normalization (sociology)', 1);
  END
  IF NOT EXISTS (SELECT 1 FROM PAPER_KEYWORD WHERE PaperID = @PaperID AND KeywordID = @KWID_0)
      INSERT INTO PAPER_KEYWORD (PaperID, KeywordID, RelevanceScore) VALUES (@PaperID, @KWID_0, 0.9325);

  DECLARE @KWID_1 UNIQUEIDENTIFIER;
  IF EXISTS (SELECT 1 FROM KEYWORD WHERE NormalizedText = N'initialization')
  BEGIN
      SET @KWID_1 = (SELECT TOP 1 KeywordID FROM KEYWORD WHERE NormalizedText = N'initialization');
      UPDATE KEYWORD SET PaperCount = ISNULL(PaperCount, 0) + 1 WHERE KeywordID = @KWID_1;
  END
  ELSE
  BEGIN
      SET @KWID_1 = NEWID();
      INSERT INTO KEYWORD (KeywordID, FieldID, KeywordText, NormalizedText, PaperCount) VALUES (@KWID_1, @FieldID, N'Initialization', N'initialization', 1);
  END
  IF NOT EXISTS (SELECT 1 FROM PAPER_KEYWORD WHERE PaperID = @PaperID AND KeywordID = @KWID_1)
      INSERT INTO PAPER_KEYWORD (PaperID, KeywordID, RelevanceScore) VALUES (@PaperID, @KWID_1, 0.9189);

  DECLARE @KWID_2 UNIQUEIDENTIFIER;
  IF EXISTS (SELECT 1 FROM KEYWORD WHERE NormalizedText = N'computer science')
  BEGIN
      SET @KWID_2 = (SELECT TOP 1 KeywordID FROM KEYWORD WHERE NormalizedText = N'computer science');
      UPDATE KEYWORD SET PaperCount = ISNULL(PaperCount, 0) + 1 WHERE KeywordID = @KWID_2;
  END
  ELSE
  BEGIN
      SET @KWID_2 = NEWID();
      INSERT INTO KEYWORD (KeywordID, FieldID, KeywordText, NormalizedText, PaperCount) VALUES (@KWID_2, @FieldID, N'Computer science', N'computer science', 1);
  END
  IF NOT EXISTS (SELECT 1 FROM PAPER_KEYWORD WHERE PaperID = @PaperID AND KeywordID = @KWID_2)
      INSERT INTO PAPER_KEYWORD (PaperID, KeywordID, RelevanceScore) VALUES (@PaperID, @KWID_2, 0.7289);

  DECLARE @KWID_3 UNIQUEIDENTIFIER;
  IF EXISTS (SELECT 1 FROM KEYWORD WHERE NormalizedText = N'artificial intelligence')
  BEGIN
      SET @KWID_3 = (SELECT TOP 1 KeywordID FROM KEYWORD WHERE NormalizedText = N'artificial intelligence');
      UPDATE KEYWORD SET PaperCount = ISNULL(PaperCount, 0) + 1 WHERE KeywordID = @KWID_3;
  END
  ELSE
  BEGIN
      SET @KWID_3 = NEWID();
      INSERT INTO KEYWORD (KeywordID, FieldID, KeywordText, NormalizedText, PaperCount) VALUES (@KWID_3, @FieldID, N'Artificial intelligence', N'artificial intelligence', 1);
  END
  IF NOT EXISTS (SELECT 1 FROM PAPER_KEYWORD WHERE PaperID = @PaperID AND KeywordID = @KWID_3)
      INSERT INTO PAPER_KEYWORD (PaperID, KeywordID, RelevanceScore) VALUES (@PaperID, @KWID_3, 0.5830);

  DECLARE @KWID_4 UNIQUEIDENTIFIER;
  IF EXISTS (SELECT 1 FROM KEYWORD WHERE NormalizedText = N'margin (machine learning)')
  BEGIN
      SET @KWID_4 = (SELECT TOP 1 KeywordID FROM KEYWORD WHERE NormalizedText = N'margin (machine learning)');
      UPDATE KEYWORD SET PaperCount = ISNULL(PaperCount, 0) + 1 WHERE KeywordID = @KWID_4;
  END
  ELSE
  BEGIN
      SET @KWID_4 = NEWID();
      INSERT INTO KEYWORD (KeywordID, FieldID, KeywordText, NormalizedText, PaperCount) VALUES (@KWID_4, @FieldID, N'Margin (machine learning)', N'margin (machine learning)', 1);
  END
  IF NOT EXISTS (SELECT 1 FROM PAPER_KEYWORD WHERE PaperID = @PaperID AND KeywordID = @KWID_4)
      INSERT INTO PAPER_KEYWORD (PaperID, KeywordID, RelevanceScore) VALUES (@PaperID, @KWID_4, 0.5588);

  DECLARE @KWID_5 UNIQUEIDENTIFIER;
  IF EXISTS (SELECT 1 FROM KEYWORD WHERE NormalizedText = N'artificial neural network')
  BEGIN
      SET @KWID_5 = (SELECT TOP 1 KeywordID FROM KEYWORD WHERE NormalizedText = N'artificial neural network');
      UPDATE KEYWORD SET PaperCount = ISNULL(PaperCount, 0) + 1 WHERE KeywordID = @KWID_5;
  END
  ELSE
  BEGIN
      SET @KWID_5 = NEWID();
      INSERT INTO KEYWORD (KeywordID, FieldID, KeywordText, NormalizedText, PaperCount) VALUES (@KWID_5, @FieldID, N'Artificial neural network', N'artificial neural network', 1);
  END
  IF NOT EXISTS (SELECT 1 FROM PAPER_KEYWORD WHERE PaperID = @PaperID AND KeywordID = @KWID_5)
      INSERT INTO PAPER_KEYWORD (PaperID, KeywordID, RelevanceScore) VALUES (@PaperID, @KWID_5, 0.5363);

  DECLARE @KWID_6 UNIQUEIDENTIFIER;
  IF EXISTS (SELECT 1 FROM KEYWORD WHERE NormalizedText = N'covariate')
  BEGIN
      SET @KWID_6 = (SELECT TOP 1 KeywordID FROM KEYWORD WHERE NormalizedText = N'covariate');
      UPDATE KEYWORD SET PaperCount = ISNULL(PaperCount, 0) + 1 WHERE KeywordID = @KWID_6;
  END
  ELSE
  BEGIN
      SET @KWID_6 = NEWID();
      INSERT INTO KEYWORD (KeywordID, FieldID, KeywordText, NormalizedText, PaperCount) VALUES (@KWID_6, @FieldID, N'Covariate', N'covariate', 1);
  END
  IF NOT EXISTS (SELECT 1 FROM PAPER_KEYWORD WHERE PaperID = @PaperID AND KeywordID = @KWID_6)
      INSERT INTO PAPER_KEYWORD (PaperID, KeywordID, RelevanceScore) VALUES (@PaperID, @KWID_6, 0.5332);

  DECLARE @KWID_7 UNIQUEIDENTIFIER;
  IF EXISTS (SELECT 1 FROM KEYWORD WHERE NormalizedText = N'training (meteorology)')
  BEGIN
      SET @KWID_7 = (SELECT TOP 1 KeywordID FROM KEYWORD WHERE NormalizedText = N'training (meteorology)');
      UPDATE KEYWORD SET PaperCount = ISNULL(PaperCount, 0) + 1 WHERE KeywordID = @KWID_7;
  END
  ELSE
  BEGIN
      SET @KWID_7 = NEWID();
      INSERT INTO KEYWORD (KeywordID, FieldID, KeywordText, NormalizedText, PaperCount) VALUES (@KWID_7, @FieldID, N'Training (meteorology)', N'training (meteorology)', 1);
  END
  IF NOT EXISTS (SELECT 1 FROM PAPER_KEYWORD WHERE PaperID = @PaperID AND KeywordID = @KWID_7)
      INSERT INTO PAPER_KEYWORD (PaperID, KeywordID, RelevanceScore) VALUES (@PaperID, @KWID_7, 0.5087);

  COMMIT TRANSACTION;
  PRINT '✅ [3/5] OK: W2949117887 — Batch Normalization: Accelerating Deep Network Tra';
END TRY
BEGIN CATCH
  IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
  DECLARE @ErrMsg NVARCHAR(MAX) = ERROR_MESSAGE();
  IF @ErrMsg LIKE '%SKIP_DOI%' OR @ErrMsg LIKE '%SKIP_TITLE%'
      PRINT '⏭ [3/5] SKIP: W2949117887 — duplicate (already in DB)';
  ELSE
  BEGIN
      PRINT '❌ [3/5] ERROR: W2949117887 — ' + @ErrMsg + ' (Line: ' + CAST(ERROR_LINE() AS NVARCHAR) + ')';
      THROW;
  END
END CATCH
GO

-- ───────────────────────────────────────────────────────────────
-- TEST PAPER [4/5] A Multi-Modal Distributed Real-Time IoT System for Urban Traffic Contr
-- OpenAlex: https://openalex.org/W4293584584
-- Year: 2024 | Citations: 14325
-- DOI: 10.4230/oasics.ng-res.2024.2 | Type: preprint
-- ───────────────────────────────────────────────────────────────

BEGIN TRY
  BEGIN TRANSACTION;
  DECLARE @SourceID UNIQUEIDENTIFIER = (SELECT SourceID FROM API_SOURCE WHERE SourceName = 'OpenAlex');
  DECLARE @PaperID UNIQUEIDENTIFIER = NEWID();
  DECLARE @JournalID UNIQUEIDENTIFIER = NULL;
  DECLARE @FieldID UNIQUEIDENTIFIER = NULL;

  -- No ISSN for journal: Leibniz-Zentrum für Informatik (Schloss Dagstuhl)
  IF NOT EXISTS (SELECT 1 FROM JOURNAL WHERE JournalName = N'Leibniz-Zentrum für Informatik (Schloss Dagstuhl)')
  BEGIN
      SET @JournalID = NEWID();
      INSERT INTO JOURNAL (JournalID, SourceID, JournalName, ISSN, Publisher, IsActive)
      VALUES (@JournalID, @SourceID, N'Leibniz-Zentrum für Informatik (Schloss Dagstuhl)', NULL, N'Schloss Dagstuhl – Leibniz Center for Informatics', 1);
  END
  ELSE
      SET @JournalID = (SELECT TOP 1 JournalID FROM JOURNAL WHERE JournalName = N'Leibniz-Zentrum für Informatik (Schloss Dagstuhl)');

  IF NOT EXISTS (SELECT 1 FROM RESEARCH_FIELD WHERE FieldName = N'Computer Science')
  BEGIN
      SET @FieldID = NEWID();
      INSERT INTO RESEARCH_FIELD (FieldID, FieldName, IsTracked)
      VALUES (@FieldID, N'Computer Science', 1);
  END
  ELSE
      SET @FieldID = (SELECT TOP 1 FieldID FROM RESEARCH_FIELD WHERE FieldName = N'Computer Science');

  -- Check duplicate by DOI
  IF EXISTS (SELECT 1 FROM RESEARCH_PAPER WHERE DOI = N'10.4230/oasics.ng-res.2024.2')
      THROW 50000, 'SKIP_DOI: W4293584584', 1;

  INSERT INTO RESEARCH_PAPER (PaperID, SourceID, JournalID, FieldID, Title, Abstract, DOI, PubDate, PubYear, CitationCount, IsOpenAccess, PdfUrl)
  VALUES (
      @PaperID, @SourceID, @JournalID, @FieldID,
      N'A Multi-Modal Distributed Real-Time IoT System for Urban Traffic Control (Invited Paper)',
      N'Traffic congestion is one of the growing urban problem with associated problems like fuel wastage, loss of lives, and slow productivity. The existing traffic system uses programming logic control (PLC) with round-robin scheduling algorithm. Recent works have proposed IoT-based frameworks that use traffic density of each lane to control traffic movement, but they suffer from low accuracy due to lack of emergency vehicle image datasets for training deep neural networks. In this paper, we propose a novel distributed IoT framework that is based on two observations. The first observation is major structural changes to road are rare. This observation is exploited by proposing a novel two stage vehicle detector that is able to achieve 77% vehicle detection accuracy on UA-DETRAC dataset. The second observation is emergency vehicle have distinct siren sound that is detected using a novel acoustic detection algorithm on an edge device. The proposed system is able to detect emergency vehicles with an average accuracy of 99.4%.',
      N'10.4230/oasics.ng-res.2024.2',
      '2024-01-01',
      2024,
      14325,
      1,
      N'https://drops.dagstuhl.de/storage/01oasics/oasics-vol117-ng-res2024/OASIcs.NG-RES.2024.2/OASIcs.NG-RES.2024.2.pdf'
  );

  -- Authors (5)
  DECLARE @AID_1 UNIQUEIDENTIFIER = NEWID();
  INSERT INTO AUTHOR (AuthorID, SourceID, ExternalAuthorID, FullName, Affiliation, HIndex, TotalCitations)
  VALUES (@AID_1, @SourceID, NULL, N'Khanam, Zeba', N'BT Security Research, Adastral Park, UK', 0, 0);

  IF NOT EXISTS (SELECT 1 FROM PAPER_AUTHOR WHERE PaperID = @PaperID AND AuthorID = @AID_1)
      INSERT INTO PAPER_AUTHOR (PaperID, AuthorID, AuthorOrder, IsCorresponding) VALUES (@PaperID, @AID_1, 1, 0);

  DECLARE @AID_2 UNIQUEIDENTIFIER = NEWID();
  INSERT INTO AUTHOR (AuthorID, SourceID, ExternalAuthorID, FullName, Affiliation, HIndex, TotalCitations)
  VALUES (@AID_2, @SourceID, NULL, N'Achari, Vejey Pradeep Suresh', N'Keele University, Keele, UK', 0, 0);

  IF NOT EXISTS (SELECT 1 FROM PAPER_AUTHOR WHERE PaperID = @PaperID AND AuthorID = @AID_2)
      INSERT INTO PAPER_AUTHOR (PaperID, AuthorID, AuthorOrder, IsCorresponding) VALUES (@PaperID, @AID_2, 2, 0);

  DECLARE @AID_3 UNIQUEIDENTIFIER = NEWID();
  INSERT INTO AUTHOR (AuthorID, SourceID, ExternalAuthorID, FullName, Affiliation, HIndex, TotalCitations)
  VALUES (@AID_3, @SourceID, NULL, N'Boukhennoufa, Issam', N'University of Essex, Colchester, UK', 0, 0);

  IF NOT EXISTS (SELECT 1 FROM PAPER_AUTHOR WHERE PaperID = @PaperID AND AuthorID = @AID_3)
      INSERT INTO PAPER_AUTHOR (PaperID, AuthorID, AuthorOrder, IsCorresponding) VALUES (@PaperID, @AID_3, 3, 0);

  DECLARE @AID_4 UNIQUEIDENTIFIER = NEWID();
  INSERT INTO AUTHOR (AuthorID, SourceID, ExternalAuthorID, FullName, Affiliation, HIndex, TotalCitations)
  VALUES (@AID_4, @SourceID, NULL, N'Jindal, Anish', N'Durham University, Durham, UK', 0, 0);

  IF NOT EXISTS (SELECT 1 FROM PAPER_AUTHOR WHERE PaperID = @PaperID AND AuthorID = @AID_4)
      INSERT INTO PAPER_AUTHOR (PaperID, AuthorID, AuthorOrder, IsCorresponding) VALUES (@PaperID, @AID_4, 4, 0);

  DECLARE @AID_5 UNIQUEIDENTIFIER = NEWID();
  INSERT INTO AUTHOR (AuthorID, SourceID, ExternalAuthorID, FullName, Affiliation, HIndex, TotalCitations)
  VALUES (@AID_5, @SourceID, NULL, N'Singh, Amit Kumar', N'University of Essex, Colchester, UK', 0, 0);

  IF NOT EXISTS (SELECT 1 FROM PAPER_AUTHOR WHERE PaperID = @PaperID AND AuthorID = @AID_5)
      INSERT INTO PAPER_AUTHOR (PaperID, AuthorID, AuthorOrder, IsCorresponding) VALUES (@PaperID, @AID_5, 5, 0);

  -- Keywords (8)
  DECLARE @KWID_0 UNIQUEIDENTIFIER;
  IF EXISTS (SELECT 1 FROM KEYWORD WHERE NormalizedText = N'computer science')
  BEGIN
      SET @KWID_0 = (SELECT TOP 1 KeywordID FROM KEYWORD WHERE NormalizedText = N'computer science');
      UPDATE KEYWORD SET PaperCount = ISNULL(PaperCount, 0) + 1 WHERE KeywordID = @KWID_0;
  END
  ELSE
  BEGIN
      SET @KWID_0 = NEWID();
      INSERT INTO KEYWORD (KeywordID, FieldID, KeywordText, NormalizedText, PaperCount) VALUES (@KWID_0, @FieldID, N'Computer science', N'computer science', 1);
  END
  IF NOT EXISTS (SELECT 1 FROM PAPER_KEYWORD WHERE PaperID = @PaperID AND KeywordID = @KWID_0)
      INSERT INTO PAPER_KEYWORD (PaperID, KeywordID, RelevanceScore) VALUES (@PaperID, @KWID_0, 0.7081);

  DECLARE @KWID_1 UNIQUEIDENTIFIER;
  IF EXISTS (SELECT 1 FROM KEYWORD WHERE NormalizedText = N'code (set theory)')
  BEGIN
      SET @KWID_1 = (SELECT TOP 1 KeywordID FROM KEYWORD WHERE NormalizedText = N'code (set theory)');
      UPDATE KEYWORD SET PaperCount = ISNULL(PaperCount, 0) + 1 WHERE KeywordID = @KWID_1;
  END
  ELSE
  BEGIN
      SET @KWID_1 = NEWID();
      INSERT INTO KEYWORD (KeywordID, FieldID, KeywordText, NormalizedText, PaperCount) VALUES (@KWID_1, @FieldID, N'Code (set theory)', N'code (set theory)', 1);
  END
  IF NOT EXISTS (SELECT 1 FROM PAPER_KEYWORD WHERE PaperID = @PaperID AND KeywordID = @KWID_1)
      INSERT INTO PAPER_KEYWORD (PaperID, KeywordID, RelevanceScore) VALUES (@PaperID, @KWID_1, 0.6448);

  DECLARE @KWID_2 UNIQUEIDENTIFIER;
  IF EXISTS (SELECT 1 FROM KEYWORD WHERE NormalizedText = N'titan (rocket family)')
  BEGIN
      SET @KWID_2 = (SELECT TOP 1 KeywordID FROM KEYWORD WHERE NormalizedText = N'titan (rocket family)');
      UPDATE KEYWORD SET PaperCount = ISNULL(PaperCount, 0) + 1 WHERE KeywordID = @KWID_2;
  END
  ELSE
  BEGIN
      SET @KWID_2 = NEWID();
      INSERT INTO KEYWORD (KeywordID, FieldID, KeywordText, NormalizedText, PaperCount) VALUES (@KWID_2, @FieldID, N'Titan (rocket family)', N'titan (rocket family)', 1);
  END
  IF NOT EXISTS (SELECT 1 FROM PAPER_KEYWORD WHERE PaperID = @PaperID AND KeywordID = @KWID_2)
      INSERT INTO PAPER_KEYWORD (PaperID, KeywordID, RelevanceScore) VALUES (@PaperID, @KWID_2, 0.6061);

  DECLARE @KWID_3 UNIQUEIDENTIFIER;
  IF EXISTS (SELECT 1 FROM KEYWORD WHERE NormalizedText = N'artificial intelligence')
  BEGIN
      SET @KWID_3 = (SELECT TOP 1 KeywordID FROM KEYWORD WHERE NormalizedText = N'artificial intelligence');
      UPDATE KEYWORD SET PaperCount = ISNULL(PaperCount, 0) + 1 WHERE KeywordID = @KWID_3;
  END
  ELSE
  BEGIN
      SET @KWID_3 = NEWID();
      INSERT INTO KEYWORD (KeywordID, FieldID, KeywordText, NormalizedText, PaperCount) VALUES (@KWID_3, @FieldID, N'Artificial intelligence', N'artificial intelligence', 1);
  END
  IF NOT EXISTS (SELECT 1 FROM PAPER_KEYWORD WHERE PaperID = @PaperID AND KeywordID = @KWID_3)
      INSERT INTO PAPER_KEYWORD (PaperID, KeywordID, RelevanceScore) VALUES (@PaperID, @KWID_3, 0.5359);

  DECLARE @KWID_4 UNIQUEIDENTIFIER;
  IF EXISTS (SELECT 1 FROM KEYWORD WHERE NormalizedText = N'metric (unit)')
  BEGIN
      SET @KWID_4 = (SELECT TOP 1 KeywordID FROM KEYWORD WHERE NormalizedText = N'metric (unit)');
      UPDATE KEYWORD SET PaperCount = ISNULL(PaperCount, 0) + 1 WHERE KeywordID = @KWID_4;
  END
  ELSE
  BEGIN
      SET @KWID_4 = NEWID();
      INSERT INTO KEYWORD (KeywordID, FieldID, KeywordText, NormalizedText, PaperCount) VALUES (@KWID_4, @FieldID, N'Metric (unit)', N'metric (unit)', 1);
  END
  IF NOT EXISTS (SELECT 1 FROM PAPER_KEYWORD WHERE PaperID = @PaperID AND KeywordID = @KWID_4)
      INSERT INTO PAPER_KEYWORD (PaperID, KeywordID, RelevanceScore) VALUES (@PaperID, @KWID_4, 0.5312);

  DECLARE @KWID_5 UNIQUEIDENTIFIER;
  IF EXISTS (SELECT 1 FROM KEYWORD WHERE NormalizedText = N'swell')
  BEGIN
      SET @KWID_5 = (SELECT TOP 1 KeywordID FROM KEYWORD WHERE NormalizedText = N'swell');
      UPDATE KEYWORD SET PaperCount = ISNULL(PaperCount, 0) + 1 WHERE KeywordID = @KWID_5;
  END
  ELSE
  BEGIN
      SET @KWID_5 = NEWID();
      INSERT INTO KEYWORD (KeywordID, FieldID, KeywordText, NormalizedText, PaperCount) VALUES (@KWID_5, @FieldID, N'Swell', N'swell', 1);
  END
  IF NOT EXISTS (SELECT 1 FROM PAPER_KEYWORD WHERE PaperID = @PaperID AND KeywordID = @KWID_5)
      INSERT INTO PAPER_KEYWORD (PaperID, KeywordID, RelevanceScore) VALUES (@PaperID, @KWID_5, 0.4596);

  DECLARE @KWID_6 UNIQUEIDENTIFIER;
  IF EXISTS (SELECT 1 FROM KEYWORD WHERE NormalizedText = N'computer vision')
  BEGIN
      SET @KWID_6 = (SELECT TOP 1 KeywordID FROM KEYWORD WHERE NormalizedText = N'computer vision');
      UPDATE KEYWORD SET PaperCount = ISNULL(PaperCount, 0) + 1 WHERE KeywordID = @KWID_6;
  END
  ELSE
  BEGIN
      SET @KWID_6 = NEWID();
      INSERT INTO KEYWORD (KeywordID, FieldID, KeywordText, NormalizedText, PaperCount) VALUES (@KWID_6, @FieldID, N'Computer vision', N'computer vision', 1);
  END
  IF NOT EXISTS (SELECT 1 FROM PAPER_KEYWORD WHERE PaperID = @PaperID AND KeywordID = @KWID_6)
      INSERT INTO PAPER_KEYWORD (PaperID, KeywordID, RelevanceScore) VALUES (@PaperID, @KWID_6, 0.3859);

  DECLARE @KWID_7 UNIQUEIDENTIFIER;
  IF EXISTS (SELECT 1 FROM KEYWORD WHERE NormalizedText = N'physics')
  BEGIN
      SET @KWID_7 = (SELECT TOP 1 KeywordID FROM KEYWORD WHERE NormalizedText = N'physics');
      UPDATE KEYWORD SET PaperCount = ISNULL(PaperCount, 0) + 1 WHERE KeywordID = @KWID_7;
  END
  ELSE
  BEGIN
      SET @KWID_7 = NEWID();
      INSERT INTO KEYWORD (KeywordID, FieldID, KeywordText, NormalizedText, PaperCount) VALUES (@KWID_7, @FieldID, N'Physics', N'physics', 1);
  END
  IF NOT EXISTS (SELECT 1 FROM PAPER_KEYWORD WHERE PaperID = @PaperID AND KeywordID = @KWID_7)
      INSERT INTO PAPER_KEYWORD (PaperID, KeywordID, RelevanceScore) VALUES (@PaperID, @KWID_7, 0.0960);

  COMMIT TRANSACTION;
  PRINT '✅ [4/5] OK: W4293584584 — A Multi-Modal Distributed Real-Time IoT System for';
END TRY
BEGIN CATCH
  IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
  DECLARE @ErrMsg NVARCHAR(MAX) = ERROR_MESSAGE();
  IF @ErrMsg LIKE '%SKIP_DOI%' OR @ErrMsg LIKE '%SKIP_TITLE%'
      PRINT '⏭ [4/5] SKIP: W4293584584 — duplicate (already in DB)';
  ELSE
  BEGIN
      PRINT '❌ [4/5] ERROR: W4293584584 — ' + @ErrMsg + ' (Line: ' + CAST(ERROR_LINE() AS NVARCHAR) + ')';
      THROW;
  END
END CATCH
GO

-- ───────────────────────────────────────────────────────────────
-- TEST PAPER [5/5] Accurate structure prediction of biomolecular interactions with AlphaF
-- OpenAlex: https://openalex.org/W4396721167
-- Year: 2024 | Citations: 14066
-- DOI: 10.1038/s41586-024-07487-w | Type: article
-- ───────────────────────────────────────────────────────────────

BEGIN TRY
  BEGIN TRANSACTION;
  DECLARE @SourceID UNIQUEIDENTIFIER = (SELECT SourceID FROM API_SOURCE WHERE SourceName = 'OpenAlex');
  DECLARE @PaperID UNIQUEIDENTIFIER = NEWID();
  DECLARE @JournalID UNIQUEIDENTIFIER = NULL;
  DECLARE @FieldID UNIQUEIDENTIFIER = NULL;

  IF NOT EXISTS (SELECT 1 FROM JOURNAL WHERE ISSN = N'0028-0836')
  BEGIN
      SET @JournalID = NEWID();
      INSERT INTO JOURNAL (JournalID, SourceID, JournalName, ISSN, Publisher, IsActive)
      VALUES (@JournalID, @SourceID, N'Nature', N'0028-0836', N'Nature Portfolio', 1);
  END
  ELSE
      SET @JournalID = (SELECT TOP 1 JournalID FROM JOURNAL WHERE ISSN = N'0028-0836');

  IF NOT EXISTS (SELECT 1 FROM RESEARCH_FIELD WHERE FieldName = N'Biochemistry, Genetics and Molecular Biology')
  BEGIN
      SET @FieldID = NEWID();
      INSERT INTO RESEARCH_FIELD (FieldID, FieldName, IsTracked)
      VALUES (@FieldID, N'Biochemistry, Genetics and Molecular Biology', 1);
  END
  ELSE
      SET @FieldID = (SELECT TOP 1 FieldID FROM RESEARCH_FIELD WHERE FieldName = N'Biochemistry, Genetics and Molecular Biology');

  -- Check duplicate by DOI
  IF EXISTS (SELECT 1 FROM RESEARCH_PAPER WHERE DOI = N'10.1038/s41586-024-07487-w')
      THROW 50000, 'SKIP_DOI: W4396721167', 1;

  INSERT INTO RESEARCH_PAPER (PaperID, SourceID, JournalID, FieldID, Title, Abstract, DOI, PubDate, PubYear, CitationCount, IsOpenAccess, PdfUrl)
  VALUES (
      @PaperID, @SourceID, @JournalID, @FieldID,
      N'Accurate structure prediction of biomolecular interactions with AlphaFold 3',
      N'Abstract The introduction of AlphaFold 2 1 has spurred a revolution in modelling the structure of proteins and their interactions, enabling a huge range of applications in protein modelling and design 2–6 . Here we describe our AlphaFold 3 model with a substantially updated diffusion-based architecture that is capable of predicting the joint structure of complexes including proteins, nucleic acids, small molecules, ions and modified residues. The new AlphaFold model demonstrates substantially improved accuracy over many previous specialized tools: far greater accuracy for protein–ligand interactions compared with state-of-the-art docking tools, much higher accuracy for protein–nucleic acid interactions compared with nucleic-acid-specific predictors and substantially higher antibody–antigen prediction accuracy compared with AlphaFold-Multimer v.2.3 7,8 . Together, these results show that high-accuracy modelling across biomolecular space is possible within a single unified deep-learning framework.',
      N'10.1038/s41586-024-07487-w',
      '2024-05-08',
      2024,
      14066,
      1,
      N'https://www.nature.com/articles/s41586-024-07487-w_reference.pdf'
  );

  -- Authors (5)
  IF NOT EXISTS (SELECT 1 FROM AUTHOR WHERE SourceID = @SourceID AND ExternalAuthorID = N'A5033390440')
  BEGIN
      DECLARE @AID_1 UNIQUEIDENTIFIER = NEWID();
      INSERT INTO AUTHOR (AuthorID, SourceID, ExternalAuthorID, FullName, Affiliation, HIndex, TotalCitations)
      VALUES (@AID_1, @SourceID, N'A5033390440', N'Josh Abramson', N'Core Contributor, Google DeepMind, London, UK', 0, 0);
  END
  ELSE
      DECLARE @AID_1 UNIQUEIDENTIFIER = (SELECT AuthorID FROM AUTHOR WHERE SourceID = @SourceID AND ExternalAuthorID = N'A5033390440');

  IF NOT EXISTS (SELECT 1 FROM PAPER_AUTHOR WHERE PaperID = @PaperID AND AuthorID = @AID_1)
      INSERT INTO PAPER_AUTHOR (PaperID, AuthorID, AuthorOrder, IsCorresponding) VALUES (@PaperID, @AID_1, 1, 0);

  IF NOT EXISTS (SELECT 1 FROM AUTHOR WHERE SourceID = @SourceID AND ExternalAuthorID = N'A5088186812')
  BEGIN
      DECLARE @AID_2 UNIQUEIDENTIFIER = NEWID();
      INSERT INTO AUTHOR (AuthorID, SourceID, ExternalAuthorID, FullName, Affiliation, HIndex, TotalCitations)
      VALUES (@AID_2, @SourceID, N'A5088186812', N'Jonas Adler', N'Core Contributor, Google DeepMind, London, UK', 0, 0);
  END
  ELSE
      DECLARE @AID_2 UNIQUEIDENTIFIER = (SELECT AuthorID FROM AUTHOR WHERE SourceID = @SourceID AND ExternalAuthorID = N'A5088186812');

  IF NOT EXISTS (SELECT 1 FROM PAPER_AUTHOR WHERE PaperID = @PaperID AND AuthorID = @AID_2)
      INSERT INTO PAPER_AUTHOR (PaperID, AuthorID, AuthorOrder, IsCorresponding) VALUES (@PaperID, @AID_2, 2, 0);

  IF NOT EXISTS (SELECT 1 FROM AUTHOR WHERE SourceID = @SourceID AND ExternalAuthorID = N'A5096909573')
  BEGIN
      DECLARE @AID_3 UNIQUEIDENTIFIER = NEWID();
      INSERT INTO AUTHOR (AuthorID, SourceID, ExternalAuthorID, FullName, Affiliation, HIndex, TotalCitations)
      VALUES (@AID_3, @SourceID, N'A5096909573', N'Jack Dunger', N'Core Contributor, Google DeepMind, London, UK', 0, 0);
  END
  ELSE
      DECLARE @AID_3 UNIQUEIDENTIFIER = (SELECT AuthorID FROM AUTHOR WHERE SourceID = @SourceID AND ExternalAuthorID = N'A5096909573');

  IF NOT EXISTS (SELECT 1 FROM PAPER_AUTHOR WHERE PaperID = @PaperID AND AuthorID = @AID_3)
      INSERT INTO PAPER_AUTHOR (PaperID, AuthorID, AuthorOrder, IsCorresponding) VALUES (@PaperID, @AID_3, 3, 0);

  DECLARE @AID_4 UNIQUEIDENTIFIER = NEWID();
  INSERT INTO AUTHOR (AuthorID, SourceID, ExternalAuthorID, FullName, Affiliation, HIndex, TotalCitations)
  VALUES (@AID_4, @SourceID, NULL, N'Richard Evans', N'Core Contributor, Google DeepMind, London, UK', 0, 0);

  IF NOT EXISTS (SELECT 1 FROM PAPER_AUTHOR WHERE PaperID = @PaperID AND AuthorID = @AID_4)
      INSERT INTO PAPER_AUTHOR (PaperID, AuthorID, AuthorOrder, IsCorresponding) VALUES (@PaperID, @AID_4, 4, 0);

  IF NOT EXISTS (SELECT 1 FROM AUTHOR WHERE SourceID = @SourceID AND ExternalAuthorID = N'A5101569668')
  BEGIN
      DECLARE @AID_5 UNIQUEIDENTIFIER = NEWID();
      INSERT INTO AUTHOR (AuthorID, SourceID, ExternalAuthorID, FullName, Affiliation, HIndex, TotalCitations)
      VALUES (@AID_5, @SourceID, N'A5101569668', N'Tim Green', N'Core Contributor, Google DeepMind, London, UK', 0, 0);
  END
  ELSE
      DECLARE @AID_5 UNIQUEIDENTIFIER = (SELECT AuthorID FROM AUTHOR WHERE SourceID = @SourceID AND ExternalAuthorID = N'A5101569668');

  IF NOT EXISTS (SELECT 1 FROM PAPER_AUTHOR WHERE PaperID = @PaperID AND AuthorID = @AID_5)
      INSERT INTO PAPER_AUTHOR (PaperID, AuthorID, AuthorOrder, IsCorresponding) VALUES (@PaperID, @AID_5, 5, 0);

  -- Keywords (4)
  DECLARE @KWID_0 UNIQUEIDENTIFIER;
  IF EXISTS (SELECT 1 FROM KEYWORD WHERE NormalizedText = N'computational biology')
  BEGIN
      SET @KWID_0 = (SELECT TOP 1 KeywordID FROM KEYWORD WHERE NormalizedText = N'computational biology');
      UPDATE KEYWORD SET PaperCount = ISNULL(PaperCount, 0) + 1 WHERE KeywordID = @KWID_0;
  END
  ELSE
  BEGIN
      SET @KWID_0 = NEWID();
      INSERT INTO KEYWORD (KeywordID, FieldID, KeywordText, NormalizedText, PaperCount) VALUES (@KWID_0, @FieldID, N'Computational biology', N'computational biology', 1);
  END
  IF NOT EXISTS (SELECT 1 FROM PAPER_KEYWORD WHERE PaperID = @PaperID AND KeywordID = @KWID_0)
      INSERT INTO PAPER_KEYWORD (PaperID, KeywordID, RelevanceScore) VALUES (@PaperID, @KWID_0, 0.4297);

  DECLARE @KWID_1 UNIQUEIDENTIFIER;
  IF EXISTS (SELECT 1 FROM KEYWORD WHERE NormalizedText = N'computer science')
  BEGIN
      SET @KWID_1 = (SELECT TOP 1 KeywordID FROM KEYWORD WHERE NormalizedText = N'computer science');
      UPDATE KEYWORD SET PaperCount = ISNULL(PaperCount, 0) + 1 WHERE KeywordID = @KWID_1;
  END
  ELSE
  BEGIN
      SET @KWID_1 = NEWID();
      INSERT INTO KEYWORD (KeywordID, FieldID, KeywordText, NormalizedText, PaperCount) VALUES (@KWID_1, @FieldID, N'Computer science', N'computer science', 1);
  END
  IF NOT EXISTS (SELECT 1 FROM PAPER_KEYWORD WHERE PaperID = @PaperID AND KeywordID = @KWID_1)
      INSERT INTO PAPER_KEYWORD (PaperID, KeywordID, RelevanceScore) VALUES (@PaperID, @KWID_1, 0.3620);

  DECLARE @KWID_2 UNIQUEIDENTIFIER;
  IF EXISTS (SELECT 1 FROM KEYWORD WHERE NormalizedText = N'biology')
  BEGIN
      SET @KWID_2 = (SELECT TOP 1 KeywordID FROM KEYWORD WHERE NormalizedText = N'biology');
      UPDATE KEYWORD SET PaperCount = ISNULL(PaperCount, 0) + 1 WHERE KeywordID = @KWID_2;
  END
  ELSE
  BEGIN
      SET @KWID_2 = NEWID();
      INSERT INTO KEYWORD (KeywordID, FieldID, KeywordText, NormalizedText, PaperCount) VALUES (@KWID_2, @FieldID, N'Biology', N'biology', 1);
  END
  IF NOT EXISTS (SELECT 1 FROM PAPER_KEYWORD WHERE PaperID = @PaperID AND KeywordID = @KWID_2)
      INSERT INTO PAPER_KEYWORD (PaperID, KeywordID, RelevanceScore) VALUES (@PaperID, @KWID_2, 0.2157);

  DECLARE @KWID_3 UNIQUEIDENTIFIER;
  IF EXISTS (SELECT 1 FROM KEYWORD WHERE NormalizedText = N'protein structure and dynamics')
  BEGIN
      SET @KWID_3 = (SELECT TOP 1 KeywordID FROM KEYWORD WHERE NormalizedText = N'protein structure and dynamics');
      UPDATE KEYWORD SET PaperCount = ISNULL(PaperCount, 0) + 1 WHERE KeywordID = @KWID_3;
  END
  ELSE
  BEGIN
      SET @KWID_3 = NEWID();
      INSERT INTO KEYWORD (KeywordID, FieldID, KeywordText, NormalizedText, PaperCount) VALUES (@KWID_3, @FieldID, N'Protein Structure and Dynamics', N'protein structure and dynamics', 1);
  END
  IF NOT EXISTS (SELECT 1 FROM PAPER_KEYWORD WHERE PaperID = @PaperID AND KeywordID = @KWID_3)
      INSERT INTO PAPER_KEYWORD (PaperID, KeywordID, RelevanceScore) VALUES (@PaperID, @KWID_3, 0.9168);

  COMMIT TRANSACTION;
  PRINT '✅ [5/5] OK: W4396721167 — Accurate structure prediction of biomolecular inte';
END TRY
BEGIN CATCH
  IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
  DECLARE @ErrMsg NVARCHAR(MAX) = ERROR_MESSAGE();
  IF @ErrMsg LIKE '%SKIP_DOI%' OR @ErrMsg LIKE '%SKIP_TITLE%'
      PRINT '⏭ [5/5] SKIP: W4396721167 — duplicate (already in DB)';
  ELSE
  BEGIN
      PRINT '❌ [5/5] ERROR: W4396721167 — ' + @ErrMsg + ' (Line: ' + CAST(ERROR_LINE() AS NVARCHAR) + ')';
      THROW;
  END
END CATCH
GO

-- ═══════════════════════════════════════════════════════════════
-- ✅ TEST COMPLETE: 5 papers processed.
--    If all printed 'OK' or 'SKIP', the pipeline works correctly!
--    You are now ready to run the full importer with real snapshot data.
-- ═══════════════════════════════════════════════════════════════
GO