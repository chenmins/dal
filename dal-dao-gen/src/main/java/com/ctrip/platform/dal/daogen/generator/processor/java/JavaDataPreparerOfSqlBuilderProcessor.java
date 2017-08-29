package com.ctrip.platform.dal.daogen.generator.processor.java;

import com.ctrip.platform.dal.daogen.CodeGenContext;
import com.ctrip.platform.dal.daogen.DalProcessor;
import com.ctrip.platform.dal.daogen.entity.ExecuteResult;
import com.ctrip.platform.dal.daogen.entity.GenTaskBySqlBuilder;
import com.ctrip.platform.dal.daogen.entity.GenTaskByTableViewSp;
import com.ctrip.platform.dal.daogen.entity.Progress;
import com.ctrip.platform.dal.daogen.enums.DatabaseCategory;
import com.ctrip.platform.dal.daogen.generator.java.JavaCodeGenContext;
import com.ctrip.platform.dal.daogen.host.java.JavaTableHost;
import com.ctrip.platform.dal.daogen.utils.DbUtils;
import com.ctrip.platform.dal.daogen.utils.TaskUtils;

import java.util.*;
import java.util.concurrent.Callable;

public class JavaDataPreparerOfSqlBuilderProcessor extends AbstractJavaDataPreparer implements DalProcessor {
    @Override
    public void process(CodeGenContext context) throws Exception {}

    public List<Callable<ExecuteResult>> prepareSqlBuilder(CodeGenContext context) throws Exception {
        final JavaCodeGenContext ctx = (JavaCodeGenContext) context;
        final Progress progress = ctx.getProgress();
        List<Callable<ExecuteResult>> results = new ArrayList<>();
        Queue<GenTaskBySqlBuilder> sqlBuilders = ctx.getSqlBuilders();
        final Queue<JavaTableHost> tableHosts = ctx.getTableHosts();
        if (sqlBuilders.size() > 0) {
            Map<String, GenTaskBySqlBuilder> tempSqlBuildres = sqlBuilderBroupBy(sqlBuilders);

            for (final Map.Entry<String, GenTaskBySqlBuilder> sqlBuilder : tempSqlBuildres.entrySet()) {
                Callable<ExecuteResult> worker = new Callable<ExecuteResult>() {
                    @Override
                    public ExecuteResult call() throws Exception {
                        ExecuteResult result = new ExecuteResult("Build Extral SQL["
                                + sqlBuilder.getValue().getAllInOneName() + "." + sqlBuilder.getKey() + "] Host");
                        progress.setOtherMessage(result.getTaskName());
                        try {
                            JavaTableHost extraTableHost = buildExtraSqlBuilderHost(ctx, sqlBuilder.getValue());
                            if (null != extraTableHost) {
                                tableHosts.add(extraTableHost);
                            }
                            result.setSuccessal(true);
                        } catch (Throwable e) {
                            TaskUtils.addError(sqlBuilder.getValue().getId(), e.getMessage());
                        }
                        return result;
                    }
                };
                results.add(worker);
            }
        }
        return results;
    }

    private Map<String, GenTaskBySqlBuilder> sqlBuilderBroupBy(Queue<GenTaskBySqlBuilder> tasks) {
        Map<String, GenTaskBySqlBuilder> map = new HashMap<>();
        if (tasks == null || tasks.size() == 0)
            return map;

        for (GenTaskBySqlBuilder task : tasks) {
            String key = String.format("%s_%s", task.getAllInOneName(), task.getTable_name());

            if (!map.containsKey(key))
                map.put(key, task);
        }

        return map;
    }

    private JavaTableHost buildExtraSqlBuilderHost(CodeGenContext context, GenTaskBySqlBuilder sqlBuilder)
            throws Exception {
        GenTaskByTableViewSp tableViewSp = new GenTaskByTableViewSp();
        tableViewSp.setCud_by_sp(false);
        tableViewSp.setPagination(false);
        tableViewSp.setAllInOneName(sqlBuilder.getAllInOneName());
        tableViewSp.setDatabaseSetName(sqlBuilder.getDatabaseSetName());
        tableViewSp.setPrefix("");
        tableViewSp.setSuffix("");

        DatabaseCategory dbCategory = DatabaseCategory.SqlServer;
        String dbType = DbUtils.getDbType(sqlBuilder.getAllInOneName());
        if (null != dbType && !dbType.equalsIgnoreCase("Microsoft SQL Server")) {
            dbCategory = DatabaseCategory.MySql;
        }

        return buildTableHost(context, tableViewSp, sqlBuilder.getTable_name(), dbCategory);
    }

}
