package com.quarkus.cms.i18n;

import static org.junit.jupiter.api.Assertions.*;

import com.quarkus.cms.core.domain.CmsEntry;
import com.quarkus.cms.core.repository.CmsEntryRepository;

import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestTransaction
class DebugLocaleTest {

  @Inject
  CmsEntryRepository entryRepository;

  @BeforeEach
  void cleanUp() {
    CmsEntry.deleteAll();
  }

  @Test
  void debugCreateAndFind() {
    // Create an entry
    CmsEntry enEntry = entryRepository.create("api::article.article",
        Map.of("title", "Hello World", "content", "Great article"), "en");
    assertNotNull(enEntry);
    assertNotNull(enEntry.documentId);
    System.err.println("DEBUG: Created entry with id=" + enEntry.id + " docId=" + enEntry.documentId + " status=" + enEntry.status);

    // Try to find it directly
    CmsEntry found = CmsEntry.findByDocumentId(enEntry.documentId, "draft", "en");
    System.err.println("DEBUG: findByDocumentId(draft) = " + (found == null ? "null" : "found id=" + found.id));

    // Try to find by document ID only
    CmsEntry byDoc = (CmsEntry) CmsEntry.find("documentId", enEntry.documentId).firstResult();
    System.err.println("DEBUG: find by documentId = " + (byDoc == null ? "null" : "found id=" + byDoc.id + " status=" + byDoc.status));

    // List all entries
    List<CmsEntry> all = CmsEntry.listAll();
    System.err.println("DEBUG: total entries = " + all.size());
    for (CmsEntry e : all) {
      System.err.println("  entry id=" + e.id + " docId=" + e.documentId + " status=" + e.status + " locale=" + e.locale);
    }

    assertNotNull(found, "Should find the entry by documentId+draft+locale");
  }
}
