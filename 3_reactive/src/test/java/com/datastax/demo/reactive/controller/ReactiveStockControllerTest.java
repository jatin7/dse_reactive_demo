/*
 * Copyright DataStax, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.datastax.demo.reactive.controller;

import static com.datastax.oss.driver.api.core.DefaultConsistencyLevel.LOCAL_QUORUM;
import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.datastax.demo.common.model.Stock;
import com.datastax.demo.reactive.repository.ReactiveStockRepository;
import com.datastax.oss.driver.api.core.metadata.Node;
import com.datastax.oss.driver.api.core.servererrors.UnavailableException;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * A unit test for the {@link ReactiveStockController}.
 *
 * <p>This test starts a full Spring application context and uses {@link MockMvc} and a mocked
 * {@link ReactiveStockRepository} bean to perform the tests.
 *
 * <p>No connection to a running cluster is required.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("unit-test")
@ComponentScan("com.datastax.demo")
class ReactiveStockControllerTest {

  @Profile("unit-test")
  @Configuration
  static class StockControllerTestConfiguration {

    @Bean
    public ReactiveStockRepository mockStockRepository() {
      return mock(ReactiveStockRepository.class);
    }
  }

  @Autowired private MockMvc mvc;

  @Autowired private ReactiveStockRepository repository;

  private String base = "/api/v1/stocks";

  private Instant i1 = Instant.parse("2019-01-01T00:00:00Z");
  private Instant i2 = Instant.parse("2020-01-01T00:00:00Z");

  private Stock stock1 = new Stock("ABC", i1, BigDecimal.valueOf(42.0));
  private Stock stock1b = new Stock("ABC", i1, BigDecimal.valueOf(43.0));
  private Stock stock2 = new Stock("ABC", i2, BigDecimal.valueOf(44.0));

  private String stock1Json = "{\"symbol\":\"ABC\",\"date\":\"20190101000000000\",\"value\":42.0}";
  private String stock1bJson = "{\"symbol\":\"ABC\",\"date\":\"20190101000000000\",\"value\":43.0}";
  private String stock2Json = "{\"symbol\":\"ABC\",\"date\":\"20200101000000000\",\"value\":44.0}";

  @BeforeEach
  void setUp() {
    Mockito.reset(repository);
  }

  /** Tests that an existing stock value can be retrieved with a GET request to its specific URI. */
  @Test
  void should_find_stock_by_id() throws Exception {
    // given
    given(repository.findById("ABC", i1)).willReturn(Mono.just(stock1));
    // when
    var result = mvc.perform(get(base + "/ABC/20190101")).andReturn();
    mvc.perform(asyncDispatch(result))
        // then
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
        .andExpect(content().json(stock1Json));
  }

  /**
   * Tests that a non-existing stock value cannot be retrieved with a GET request to its specific
   * URI.
   */
  @Test
  void should_not_find_stock_by_id() throws Exception {
    // given
    given(repository.findById("ABC", i1)).willReturn(Mono.empty());
    // when
    var result = mvc.perform(get(base + "/ABC/20190101")).andReturn();
    mvc.perform(asyncDispatch(result))
        // then
        .andExpect(status().isNotFound());
  }

  /**
   * Tests that an error while retrieving a stock value with a GET request to its specific URI will
   * result in an HTTP error status.
   */
  @Test
  void should_error_out_when_find_stock_by_id_failed() throws Exception {
    // given
    var error = new UnavailableException(mock(Node.class), LOCAL_QUORUM, 2, 1);
    given(repository.findById("ABC", i1)).willReturn(Mono.error(error));
    // when
    var result = mvc.perform(get(base + "/ABC/20190101")).andReturn();
    mvc.perform(asyncDispatch(result))
        // then
        .andExpect(status().is5xxServerError());
  }

  /**
   * Tests that existing stock values for a given symbol and within a given date range can be
   * retrieved with a GET request to the appropriate URI.
   */
  @Test
  void should_find_stocks_by_symbol() throws Exception {
    // given
    given(repository.findAllBySymbol("ABC", i1, i2, 0, 10)).willReturn(Flux.just(stock1, stock2));
    // when
    var result = mvc.perform(get(base + "/ABC?start=2019&end=2020&offset=0&limit=10")).andReturn();
    mvc.perform(asyncDispatch(result))
        // then
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
        .andExpect(content().json("[" + stock1Json + "," + stock2Json + "]"));
  }

  /**
   * Tests that an error while retrieving existing stock values for a given symbol will result in an
   * HTTP error status.
   */
  @Test
  void should_error_out_when_find_stocks_by_symbol_failed() throws Exception {
    // given
    var error = new UnavailableException(mock(Node.class), LOCAL_QUORUM, 2, 1);
    given(repository.findAllBySymbol("ABC", i1, i2, 0, 10)).willReturn(Flux.error(error));
    // when
    var result = mvc.perform(get(base + "/ABC?start=2019&end=2020&offset=0&limit=10")).andReturn();
    mvc.perform(asyncDispatch(result))
        // then
        .andExpect(status().is5xxServerError());
  }

  /** Tests that a stock value can be created via a POST request to the appropriate URI. */
  @Test
  void should_create_stock() throws Exception {
    // given
    given(repository.save(stock1)).willReturn(Mono.just(stock1));
    // when
    var result =
        mvc.perform(post(base).contentType(APPLICATION_JSON).content(stock1Json)).andReturn();
    mvc.perform(asyncDispatch(result))
        // then
        .andExpect(status().isCreated())
        .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
        .andExpect(header().string("Location", endsWith(base + "/ABC/20190101000000000")))
        .andExpect(content().json(stock1Json));
    verify(repository).save(stock1);
  }

  /** Tests that an error while creating a stock value will result in an HTTP error status. */
  @Test
  void should_error_out_when_create_stock_failed() throws Exception {
    // given
    var error = new UnavailableException(mock(Node.class), LOCAL_QUORUM, 2, 1);
    given(repository.save(stock1)).willReturn(Mono.error(error));
    // when
    var result =
        mvc.perform(post(base).contentType(APPLICATION_JSON).content(stock1Json)).andReturn();
    mvc.perform(asyncDispatch(result))
        // then
        .andExpect(status().is5xxServerError());
  }

  /** Tests that an existing stock value can be updated via a PUT request to its specific URI. */
  @Test
  void should_update_stock() throws Exception {
    // given
    given(repository.findById("ABC", i1)).willReturn(Mono.just(stock1));
    given(repository.save(stock1b)).willReturn(Mono.just(stock1b));
    // when
    var result =
        mvc.perform(put(base + "/ABC/20190101").contentType(APPLICATION_JSON).content(stock1bJson))
            .andReturn();
    mvc.perform(asyncDispatch(result))
        // then
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
        .andExpect(content().json(stock1bJson));
    verify(repository).save(stock1b);
  }

  /**
   * Tests that a non-existing stock value cannot be updated via a PUT request to its specific URI.
   */
  @Test
  void should_not_update_stock() throws Exception {
    // given
    given(repository.findById("ABC", i1)).willReturn(Mono.empty());
    // when
    var result =
        mvc.perform(put(base + "/ABC/20190101").contentType(APPLICATION_JSON).content(stock1bJson))
            .andReturn();
    mvc.perform(asyncDispatch(result))
        // then
        .andExpect(status().isNotFound());
    verify(repository, never()).save(any());
  }

  /** Tests that an error updating a stock value will result in an HTTP error status. */
  @Test
  void should_error_out_when_update_stock_failed_while_finding_by_id() throws Exception {
    // given
    var error = new UnavailableException(mock(Node.class), LOCAL_QUORUM, 2, 1);
    given(repository.findById("ABC", i1)).willReturn(Mono.error(error));
    // when
    var result =
        mvc.perform(put(base + "/ABC/20190101").contentType(APPLICATION_JSON).content(stock1bJson))
            .andReturn();
    mvc.perform(asyncDispatch(result))
        // then
        .andExpect(status().is5xxServerError());
  }

  /** Tests that an error updating a stock value will result in an HTTP error status. */
  @Test
  void should_error_out_when_update_stock_failed_while_saving() throws Exception {
    // given
    var error = new UnavailableException(mock(Node.class), LOCAL_QUORUM, 2, 1);
    given(repository.findById("ABC", i1)).willReturn(Mono.just(stock1));
    given(repository.save(stock1b)).willReturn(Mono.error(error));
    // when
    var result =
        mvc.perform(put(base + "/ABC/20190101").contentType(APPLICATION_JSON).content(stock1bJson))
            .andReturn();
    mvc.perform(asyncDispatch(result))
        // then
        .andExpect(status().is5xxServerError());
  }

  /** Tests that an existing stock value can be deleted via a DELETE request to its specific URI. */
  @Test
  void should_delete_stock() throws Exception {
    // given
    given(repository.deleteById("ABC", i1)).willReturn(Mono.empty());
    // when
    var result = mvc.perform(delete(base + "/ABC/20190101")).andReturn();
    mvc.perform(asyncDispatch(result))
        // then
        .andExpect(status().isOk());
    verify(repository).deleteById("ABC", i1);
  }

  /** Tests that an error deleting a stock value will result in an HTTP error status. */
  @Test
  void should_error_out_when_delete_stock_failed() throws Exception {
    // given
    var error = new UnavailableException(mock(Node.class), LOCAL_QUORUM, 2, 1);
    given(repository.deleteById("ABC", i1)).willReturn(Mono.error(error));
    // when
    var result = mvc.perform(delete(base + "/ABC/20190101")).andReturn();
    mvc.perform(asyncDispatch(result))
        // then
        .andExpect(status().is5xxServerError());
  }
}
