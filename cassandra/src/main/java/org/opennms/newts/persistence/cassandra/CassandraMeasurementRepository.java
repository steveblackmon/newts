package org.opennms.newts.persistence.cassandra;


import static com.datastax.driver.core.querybuilder.QueryBuilder.batch;
import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static com.datastax.driver.core.querybuilder.QueryBuilder.gte;
import static com.datastax.driver.core.querybuilder.QueryBuilder.insertInto;
import static com.datastax.driver.core.querybuilder.QueryBuilder.lte;

import java.util.Collection;

import javax.inject.Named;

import org.opennms.newts.api.Duration;
import org.opennms.newts.api.Measurement;
import org.opennms.newts.api.MeasurementRepository;
import org.opennms.newts.api.Results;
import org.opennms.newts.api.Timestamp;
import org.opennms.newts.api.ValueType;
import org.opennms.newts.api.query.ResultDescriptor;

import com.codahale.metrics.MetricRegistry;
import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.Batch;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.datastax.driver.core.querybuilder.Select;
import com.google.common.base.Optional;
import com.google.inject.Inject;


public class CassandraMeasurementRepository implements MeasurementRepository {

    private Session m_session;
    @SuppressWarnings("unused") private MetricRegistry m_registry;

    @Inject
    public CassandraMeasurementRepository(@Named("cassandraKeyspace") String keyspace, @Named("cassandraHost") String host, @Named("cassandraPort") int port, MetricRegistry registry) {

        Cluster cluster = Cluster.builder().withPort(port).addContactPoint(host).build();
        m_session = cluster.connect(keyspace);

        m_registry = registry;

    }

    @Override
    public Results select(String resource, Optional<Timestamp> start, Optional<Timestamp> end, ResultDescriptor descriptor, Duration resolution) {

        Timestamp upper = end.isPresent() ? end.get() : Timestamp.now();
        @SuppressWarnings("unused") Timestamp lower = start.isPresent() ? start.get() : upper.minus(Duration.seconds(86400));

        Results measurements = new Results();

        // TODO: do.

        return measurements;
    }

    @Override
    public Results select(String resource, Optional<Timestamp> start, Optional<Timestamp> end) {

        Timestamp upper = end.isPresent() ? end.get() : Timestamp.now();
        Timestamp lower = start.isPresent() ? start.get() : upper.minus(Duration.seconds(86400));

        Results measurements = new Results();

        for (Results.Row row : new DriverAdapter(cassandraSelect(resource, lower, upper))) {
            measurements.addRow(row);
        }

        return measurements;
    }

    @Override
    public void insert(Collection<Measurement> measurements) {

        Batch batch = batch();

        for (Measurement m : measurements) {
            batch.add(
                    insertInto(SchemaConstants.T_MEASUREMENTS)
                        .value(SchemaConstants.F_RESOURCE, m.getResource())
                        .value(SchemaConstants.F_COLLECTED, m.getTimestamp().asMillis())
                        .value(SchemaConstants.F_METRIC_NAME, m.getName())
                        .value(SchemaConstants.F_METRIC_TYPE, m.getType().toString())
                        .value(SchemaConstants.F_VALUE, ValueType.decompose(m.getValue()))
                        .value(SchemaConstants.F_ATTRIBUTES, m.getAttributes())
            );
        }

        m_session.execute(batch);

    }

    // FIXME: Use a prepared statement for this.
    private ResultSet cassandraSelect(String resource, Timestamp start, Timestamp end) {

        Select select = QueryBuilder.select().from(SchemaConstants.T_MEASUREMENTS);
        select.where(eq(SchemaConstants.F_RESOURCE, resource));

        select.where(gte(SchemaConstants.F_COLLECTED, start.asDate()));
        select.where(lte(SchemaConstants.F_COLLECTED, end.asDate()));

        return m_session.execute(select);
    }

}