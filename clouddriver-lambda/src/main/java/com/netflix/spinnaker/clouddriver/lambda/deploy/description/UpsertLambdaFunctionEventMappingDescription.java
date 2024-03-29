/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.lambda.deploy.description;

import com.amazonaws.services.lambda.model.DestinationConfig;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class UpsertLambdaFunctionEventMappingDescription extends AbstractLambdaFunctionDescription {
  String functionName;
  String qualifier;
  Integer batchsize = 1;
  Boolean bisectBatchOnError = null;
  Integer maxBatchingWindowSecs = null;
  Integer maxRecordAgeSecs = null;
  Integer maxRetryAttempts = null;
  Integer parallelizationFactor = null;
  Integer tumblingWindowSecs = null;
  Boolean enabled = false;
  String eventSourceArn = null;
  String uuid = null;
  String startingPosition = null;
  DestinationConfig destinationConfig = null;
}
