/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.facebook.presto.verifier.framework;

import com.facebook.presto.spi.type.TypeManager;
import com.facebook.presto.sql.tree.Query;
import com.facebook.presto.verifier.checksum.ChecksumResult;
import com.facebook.presto.verifier.checksum.ChecksumValidator;
import com.facebook.presto.verifier.prestoaction.PrestoAction;
import com.facebook.presto.verifier.resolver.FailureResolverManager;
import com.facebook.presto.verifier.rewrite.QueryRewriter;

import java.util.List;

import static com.facebook.presto.verifier.framework.DataVerificationUtil.executeChecksumQuery;
import static com.facebook.presto.verifier.framework.DataVerificationUtil.getColumns;
import static com.facebook.presto.verifier.framework.DataVerificationUtil.match;
import static com.facebook.presto.verifier.framework.VerifierUtil.callWithQueryStatsConsumer;
import static com.google.common.collect.Iterables.getOnlyElement;
import static java.util.Objects.requireNonNull;

public class DataVerification
        extends AbstractVerification
{
    private final TypeManager typeManager;
    private final ChecksumValidator checksumValidator;

    public DataVerification(
            VerificationResubmitter verificationResubmitter,
            PrestoAction prestoAction,
            SourceQuery sourceQuery,
            QueryRewriter queryRewriter,
            DeterminismAnalyzer determinismAnalyzer,
            FailureResolverManager failureResolverManager,
            VerificationContext verificationContext,
            VerifierConfig verifierConfig,
            TypeManager typeManager,
            ChecksumValidator checksumValidator)
    {
        super(verificationResubmitter, prestoAction, sourceQuery, queryRewriter, determinismAnalyzer, failureResolverManager, verificationContext, verifierConfig);
        this.typeManager = requireNonNull(typeManager, "typeManager is null");
        this.checksumValidator = requireNonNull(checksumValidator, "checksumValidator is null");
    }

    @Override
    public MatchResult verify(QueryBundle control, QueryBundle test)
    {
        List<Column> controlColumns = getColumns(getPrestoAction(), typeManager, control.getTableName());
        List<Column> testColumns = getColumns(getPrestoAction(), typeManager, test.getTableName());

        Query controlChecksumQuery = checksumValidator.generateChecksumQuery(control.getTableName(), controlColumns);
        Query testChecksumQuery = checksumValidator.generateChecksumQuery(test.getTableName(), testColumns);

        getVerificationContext().setControlChecksumQuery(formatSql(controlChecksumQuery));
        getVerificationContext().setTestChecksumQuery(formatSql(testChecksumQuery));

        QueryResult<ChecksumResult> controlChecksum = callWithQueryStatsConsumer(
                () -> executeChecksumQuery(getPrestoAction(), controlChecksumQuery),
                stats -> getVerificationContext().setControlChecksumQueryId(stats.getQueryId()));
        QueryResult<ChecksumResult> testChecksum = callWithQueryStatsConsumer(
                () -> executeChecksumQuery(getPrestoAction(), testChecksumQuery),
                stats -> getVerificationContext().setTestChecksumQueryId(stats.getQueryId()));

        return match(checksumValidator, controlColumns, testColumns, getOnlyElement(controlChecksum.getResults()), getOnlyElement(testChecksum.getResults()));
    }
}
