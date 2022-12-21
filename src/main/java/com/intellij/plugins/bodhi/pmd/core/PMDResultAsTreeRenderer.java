package com.intellij.plugins.bodhi.pmd.core;

import com.intellij.plugins.bodhi.pmd.tree.PMDErrorBranchNode;
import com.intellij.plugins.bodhi.pmd.tree.PMDRuleNode;
import com.intellij.plugins.bodhi.pmd.tree.PMDRuleSetEntryNode;
import com.intellij.plugins.bodhi.pmd.tree.PMDSuppressedBranchNode;
import com.intellij.plugins.bodhi.pmd.tree.PMDTreeNodeFactory;
import com.intellij.plugins.bodhi.pmd.tree.PMDUselessSuppressionBranchNode;
import net.sourceforge.pmd.Report;
import net.sourceforge.pmd.Rule;
import net.sourceforge.pmd.RuleViolation;
import net.sourceforge.pmd.renderers.AbstractIncrementingRenderer;

import java.util.*;

/**
 * Represents the renderer for the PMD results in a tree.
 * Only core package classes are coupled with the PMD Library.
 *
 * @author jborgers
 */
public class PMDResultAsTreeRenderer extends AbstractIncrementingRenderer {

    private final List<PMDRuleSetEntryNode> pmdRuleResultNodes;
    private final PMDErrorBranchNode processingErrorsNode;
    private final Set<String> filesWithError = new HashSet<>();
    private final UselessSuppressionsHelper uselessSupHelper;
    private final Map<RuleKey, PMDRuleNode> ruleKeyToNodeMap = new TreeMap<>(); // order by priority and then name

    public PMDResultAsTreeRenderer(List<PMDRuleSetEntryNode> pmdRuleSetResults, PMDErrorBranchNode errorsNode, String ruleSetPath) {
        super("pmdplugin", "PMD plugin renderer");
        this.pmdRuleResultNodes = pmdRuleSetResults;
        processingErrorsNode = errorsNode;
        uselessSupHelper = new UselessSuppressionsHelper(ruleSetPath);
    }

    @Override
    public void renderFileViolations(Iterator<RuleViolation> violations) {
        PMDTreeNodeFactory nodeFactory = PMDTreeNodeFactory.getInstance();
        while (violations.hasNext()) {
            RuleViolation ruleViolation = violations.next();
            PMDResultCollector.getReport().addRuleViolation(ruleViolation);
            Rule rule = ruleViolation.getRule();
            RuleKey key = new RuleKey(rule);
            PMDRuleNode ruleNode = ruleKeyToNodeMap.get(key);
            if (ruleNode == null) {
                ruleNode = nodeFactory.createRuleNode(rule);
                ruleNode.setToolTip(rule.getDescription());
                ruleKeyToNodeMap.put(key, ruleNode);
            }
            ruleNode.add(nodeFactory.createViolationLeafNode(new PMDViolation(ruleViolation)));
            uselessSupHelper.storeRuleNameForMethod(ruleViolation);
        }
        for (PMDRuleNode ruleNode : ruleKeyToNodeMap.values()) {
            if (ruleNode.getChildCount() > 0 && !pmdRuleResultNodes.contains(ruleNode)) {
                pmdRuleResultNodes.add(ruleNode);
            }
        }
    }

    private void renderErrors() {
        if (!errors.isEmpty()) {
            PMDTreeNodeFactory nodeFactory = PMDTreeNodeFactory.getInstance();
            for (Report.ProcessingError error : errors) {
                if (!filesWithError.contains(error.getFile())) {
                    processingErrorsNode.add(nodeFactory.createErrorLeafNode(new PMDProcessingError(error)));
                    filesWithError.add(error.getFile());
                }
            }
        }
    }

    @Override
    public void end() {
        renderSuppressedViolations();
        renderUselessSuppressions();
        renderErrors();
    }

    private void renderSuppressedViolations() {
        if (!suppressed.isEmpty()) {
            PMDTreeNodeFactory nodeFactory = PMDTreeNodeFactory.getInstance();
            PMDSuppressedBranchNode suppressedByNoPmdNode = nodeFactory.createSuppressedBranchNode("Suppressed violations by //NOPMD");
            PMDSuppressedBranchNode suppressedByAnnotationNode = nodeFactory.createSuppressedBranchNode("Suppressed violations by Annotation");
            for (Report.SuppressedViolation suppressed : suppressed) {
                if (suppressed.suppressedByAnnotation()) {
                    suppressedByAnnotationNode.add(nodeFactory.createSuppressedLeafNode(new PMDSuppressedViolation(suppressed)));
                    uselessSupHelper.storeRuleNameForMethod(suppressed);
                } else {
                    suppressedByNoPmdNode.add(nodeFactory.createSuppressedLeafNode(new PMDSuppressedViolation(suppressed)));
                }
            }
            suppressedByAnnotationNode.calculateCounts();
            if (suppressedByAnnotationNode.getSuppressedCount() > 0) {
                pmdRuleResultNodes.add(suppressedByAnnotationNode);
            }
            suppressedByNoPmdNode.calculateCounts();
            if (suppressedByNoPmdNode.getSuppressedCount() > 0) {
                pmdRuleResultNodes.add(suppressedByNoPmdNode);
            }
        }
    }

    private void renderUselessSuppressions() {
        List<PMDUselessSuppression> uselessSuppressions = uselessSupHelper.findUselessSuppressions(ruleKeyToNodeMap);
        if (!uselessSuppressions.isEmpty()) {
            PMDTreeNodeFactory nodeFactory = PMDTreeNodeFactory.getInstance();
            PMDUselessSuppressionBranchNode uselessSuppressionNode = nodeFactory.createUselessSuppressionBranchNode("Useless suppressions");
            for (PMDUselessSuppression uselessSuppression : uselessSuppressions) {
                uselessSuppressionNode.add(nodeFactory.createUselessSuppressionLeafNode(uselessSuppression));
            }
            uselessSuppressionNode.calculateCounts();
            if (uselessSuppressionNode.getViolationCount() > 0) {
                pmdRuleResultNodes.add(uselessSuppressionNode);
            }
        }
    }

    public String defaultFileExtension() {
        return "txt";
    }
}