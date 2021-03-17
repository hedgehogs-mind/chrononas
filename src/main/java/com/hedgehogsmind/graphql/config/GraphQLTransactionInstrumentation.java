package com.hedgehogsmind.graphql.config;

import graphql.ExecutionResult;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.SimpleInstrumentation;
import graphql.execution.instrumentation.SimpleInstrumentationContext;
import graphql.execution.instrumentation.parameters.InstrumentationExecuteOperationParameters;
import graphql.language.OperationDefinition;
import io.micronaut.transaction.SynchronousTransactionManager;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.TransactionStatus;
import io.micronaut.transaction.support.DefaultTransactionDefinition;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@Slf4j
public class GraphQLTransactionInstrumentation extends SimpleInstrumentation {

    private static final TransactionDefinition GRAPHQL_TRANSACTION_DEFINITION = new DefaultTransactionDefinition(){
        {
            setIsolationLevel(Isolation.DEFAULT);
            setName("graphql-transaction");
            setPropagationBehavior(Propagation.REQUIRED);
        }
    };

    @Inject
    private SynchronousTransactionManager transactionManager;

    @Override
    public InstrumentationContext<ExecutionResult> beginExecuteOperation(InstrumentationExecuteOperationParameters parameters) {

        final DefaultTransactionDefinition transactionDefinition = new DefaultTransactionDefinition(GRAPHQL_TRANSACTION_DEFINITION);
        transactionDefinition.setReadOnly(
                !OperationDefinition.Operation.MUTATION.equals(parameters.getExecutionContext().getOperationDefinition().getOperation())
        );

        final TransactionStatus transactionStatus = transactionManager.getTransaction(transactionDefinition);


        return new SimpleInstrumentationContext() {
            @Override
            public void onCompleted(Object result, Throwable t) {
                if (transactionStatus.isRollbackOnly() || t != null) {
                    transactionManager.rollback(transactionStatus);
                    log.debug("Rolling back transaction for GraphQL execution: {}", Thread.currentThread().getName());
                } else {
                    transactionManager.commit(transactionStatus);
                    log.debug("Commiting transaction for GraphQL execution: {}", Thread.currentThread().getName());
                }
            }
        };
    }
}
