package kr.co.shineware.nlp.komoran.core.model;

import kr.co.shineware.ds.aho_corasick.FindContext;
import kr.co.shineware.nlp.komoran.constant.SEJONGTAGS;
import kr.co.shineware.nlp.komoran.constant.SYMBOL;
import kr.co.shineware.nlp.komoran.core.model.combinationrules.CombinationRuleChecker;
import kr.co.shineware.nlp.komoran.model.MorphTag;
import kr.co.shineware.nlp.komoran.model.ScoredTag;
import kr.co.shineware.nlp.komoran.modeler.model.*;
import kr.co.shineware.util.common.collection.MapUtil;
import kr.co.shineware.util.common.model.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Lattice {

    private static final int IRREGULAR_POS_ID = -1;
    private Map<Integer, List<LatticeNode>> lattice;
    private PosTable posTable;
    private Transition transition;
    private int lastIdx = -1;
    private int irrIdx = 0;
    private Observation observation;
    private Observation userDicObservation;
    private IrregularTrie irregularTrie;

    private FindContext<List<ScoredTag>> observationFindContext;
    private FindContext<List<IrregularNode>> irregularFindContext;
    private FindContext<List<ScoredTag>> userDicFindContext;

    private final CombinationRuleChecker combinationRuleChecker;

    private double prevMaxScore;
    private LatticeNode prevMaxNode;
    private int prevMaxIdx;
    private int nbest;

    public Lattice(Resources resource, Observation userDic) {
        this(resource, userDic, 1, null);
    }

    public Lattice(Resources resource, Observation userDic, int nbest, CombinationRuleChecker combinationRuleChecker) {
        this.setPosTable(resource.getTable());
        this.setTransition(resource.getTransition());
        this.setObservation(resource.getObservation());
        this.setIrregularTrie(resource.getIrrTrie());
        this.setUserDicObservation(userDic);
        this.init();
        this.makeNewContexts();
        this.nbest = nbest;
        this.combinationRuleChecker = combinationRuleChecker;
    }

    private void setUserDicObservation(Observation userDic) {
        this.userDicObservation = userDic;
    }

    private void setIrregularTrie(IrregularTrie irrTrie) {
        this.irregularTrie = irrTrie;
    }

    private void makeNewContexts() {
        this.observationFindContext = this.observation.getTrieDictionary().newFindContext();
        this.irregularFindContext = this.irregularTrie.getTrieDictionary().newFindContext();
        if (this.userDicObservation != null) {
            this.userDicFindContext = this.userDicObservation.getTrieDictionary().newFindContext();
        }
    }

    public Map<String, List<ScoredTag>> retrievalObservation(char jaso) {
        return this.observation.getTrieDictionary().get(this.observationFindContext, jaso);
    }

    public Map<String, List<IrregularNode>> retrievalIrregularNodes(char jaso) {
        return this.irregularTrie.getTrieDictionary().get(this.irregularFindContext, jaso);
    }

    public Map<String, List<ScoredTag>> retrievalUserDicObservation(char jaso) {
        if (this.userDicObservation == null) {
            return null;
        }

        return this.userDicObservation.getTrieDictionary().get(this.userDicFindContext, jaso);
    }

    private void init() {

        this.lattice = new HashMap<>();
        irrIdx = 0;

        List<LatticeNode> latticeNodes = new ArrayList<>();
        latticeNodes.add(this.makeStartNode());

        this.lattice.put(0, latticeNodes);
    }

    private LatticeNode makeStartNode() {
        return new LatticeNode(-1, 0, new MorphTag(SYMBOL.BOE, SYMBOL.BOE, SEJONGTAGS.BOE_ID), 0);
    }

    //????????? ????????? ?????? lattice put
    public void put(int beginIdx, int endIdx,
                    List<Pair<String, String>> fwdResultList) {

        if (fwdResultList.size() == 1) {
            Pair<String, String> morphPosPair = fwdResultList.get(0);
            this.put(beginIdx, endIdx, morphPosPair.getFirst(), morphPosPair.getSecond(), this.posTable.getId(morphPosPair.getSecond()), 0.0);
        }

        //TODO : find solution for better code to simplify calculation of FWD transition score
        //??? ????????? ??????? ??? ????????? ????????? ?????????..
        //???..????????? ????????? ?????? ???????????? ????????? ???????????? ??? ????????? ?????? ??????????????? ??????????????????
        //?????? ????????? irrIdx?????? ????????? index??? ?????? ????????? ????????????.. ?????? ?????????????
        //?????? ?????? ??? ????????? ????????? ???????????? ????????? ??? ??????. ????????? ????????? ??????. ?????? irrIdx??? ?????? ????????? ?????? ?????? ??? ????????? ?????????
        //????????? ??????..??????????????? lattice ????????? thread safe ?????? ?????????.. lattice??? ???????????? ????????? thread safe?????? ????????? ??? ????????? ????????? ????????????.. ????????? ??????????????????..????????? ??? ?????????..
        else {
            for (int i = 0; i < fwdResultList.size(); i++) {
                Pair<String, String> morphPosPair = fwdResultList.get(i);
                if (i == 0) {
                    this.put(beginIdx, irrIdx - 1, morphPosPair.getFirst(), morphPosPair.getSecond(), this.posTable.getId(morphPosPair.getSecond()), 0.0);
                } else if (i == fwdResultList.size() - 1) {
                    this.put(irrIdx, endIdx, morphPosPair.getFirst(), morphPosPair.getSecond(), this.posTable.getId(morphPosPair.getSecond()), 0.0);
                } else {
                    this.put(irrIdx, irrIdx - 1, morphPosPair.getFirst(), morphPosPair.getSecond(), this.posTable.getId(morphPosPair.getSecond()), 0.0);
                }
                irrIdx--;
            }
        }
    }

    public void put(int beginIdx, int endIdx, IrregularNode irregularNode) {
        //?????? node??? ?????? ?????? ?????? node list?????? ?????????
        List<LatticeNode> prevLatticeNodes = this.lattice.get(beginIdx);

        //??? ?????? ?????? ????????? ????????????....
        if (prevLatticeNodes != null) {
            this.prevMaxIdx = -1;
            this.prevMaxNode = null;
            this.prevMaxScore = Double.NEGATIVE_INFINITY;
            this.getMaxTransitionIdxFromPrevNodes(prevLatticeNodes, irregularNode.getFirstPosId());

            if (this.prevMaxNode != null) {
                List<Pair<String, Integer>> irregularTokens = irregularNode.getTokens();
                //?????????????????? ?????? ?????? ??????
                int prevMaxIdx = this.prevMaxIdx;
                double prevMaxScore = this.prevMaxScore;
                this.putIrregularExtendTokens(beginIdx, endIdx, irregularTokens, prevMaxScore, prevMaxIdx);

                //?????? ???????????? ????????? ???????????? ?????? ??????
//                this.putFirstIrregularNode(beginIdx, endIdx, irregularTokens, prevMaxScore, prevMaxIdx);
//                this.putIrregularTokens(beginIdx, endIdx, irregularTokens);
            }
        }
    }

    private void putIrregularExtendTokens(int beginIdx, int endIdx,
                                          List<Pair<String, Integer>> irregularTokens, double prevMaxScore, int prevMaxIdx) {
    	
        if (irregularTokens == null || irregularTokens.size() == 0) {
            return;
        }
        
    	Pair<String, Integer> morphPosPair = null;
    	List<ScoredTag> scoredTags = null;

    	if (irregularTokens.size() == 1) {
        	morphPosPair = irregularTokens.get(0);
            scoredTags = this.observation.getTrieDictionary().getValue(morphPosPair.getFirst());
            for (ScoredTag scoredTag : scoredTags) {
                if (scoredTag.getTagId() == morphPosPair.getSecond()) {
                    LatticeNode firstIrregularNode = this.makeNode(beginIdx, endIdx, morphPosPair.getFirst(), scoredTag.getTag(), scoredTag.getTagId(), prevMaxScore + scoredTag.getScore(), prevMaxIdx);
                    this.appendNode(firstIrregularNode);
                    //????????? ????????? EC??? ???????????? EF??? ???????????? ????????? ????????????
                    if (scoredTag.getTagId() == SEJONGTAGS.EC_ID) {
                        LatticeNode extendIrregularNode = this.makeNode(beginIdx, endIdx, morphPosPair.getFirst(), SYMBOL.EF, this.posTable.getId(SYMBOL.EF), prevMaxScore + scoredTag.getScore(), prevMaxIdx);
                        this.appendNode(extendIrregularNode);
                    }
                }
            }
            return;
        } 
        
        //????????? ????????? ?????? ??????
       	morphPosPair = irregularTokens.get(0);
        scoredTags = this.observation.getTrieDictionary().getValue(morphPosPair.getFirst());
        for (ScoredTag scoredTag : scoredTags) {
            if (scoredTag.getTagId() == morphPosPair.getSecond()) {
                LatticeNode firstIrregularNode = this.makeNode(beginIdx, irrIdx - 1, morphPosPair.getFirst(), scoredTag.getTag(), scoredTag.getTagId(), prevMaxScore + scoredTag.getScore(), prevMaxIdx);
                irrIdx--;
                this.appendNode(firstIrregularNode);
            }
        }

        for (int i = 1; i < irregularTokens.size(); i++) {
        	morphPosPair = irregularTokens.get(i);
        	scoredTags = this.observation.getTrieDictionary().getValue(morphPosPair.getFirst());
            //????????? ????????? ???????????? IRR ????????? ????????? ?????? score??? 0.0??? ???
            if (i == irregularTokens.size() - 1) {
                for (ScoredTag scoredTag : scoredTags) {
                    if (scoredTag.getTagId() == morphPosPair.getSecond()) {
                        this.put(irrIdx, endIdx, morphPosPair.getFirst(), this.posTable.getPos(morphPosPair.getSecond()), morphPosPair.getSecond(), scoredTag.getScore());
                        if (morphPosPair.getSecond() == SEJONGTAGS.EC_ID) {
                            this.put(irrIdx, endIdx, morphPosPair.getFirst(), SYMBOL.EF, SEJONGTAGS.EF_ID, scoredTag.getScore());
                        }
                    }
                }
                LatticeNode latticeNode = this.makeNode(irrIdx, endIdx, morphPosPair.getFirst(), SYMBOL.IRREGULAR, IRREGULAR_POS_ID, 0.0, 0);
                this.appendNode(latticeNode);

            } else {
                for (ScoredTag scoredTag : scoredTags) {
                    if (scoredTag.getTagId() == morphPosPair.getSecond()) {
                        this.put(irrIdx, irrIdx - 1, morphPosPair.getFirst(), this.posTable.getPos(morphPosPair.getSecond()), morphPosPair.getSecond(), scoredTag.getScore());
                    }
                }
            }
            irrIdx--;
        }
    }
/*
    private void putFirstIrregularNode(int beginIdx, int endIdx,
                                       List<Pair<String, Integer>> irregularTokens, double score,
                                       int maxTransitionPrevIdx) {
        if (irregularTokens.size() == 1) {
            Pair<String, Integer> morphPosId = irregularTokens.get(0);
            List<ScoredTag> scoredTags = this.observation.getTrieDictionary().getValue(morphPosId.getFirst());
            for (ScoredTag scoredTag : scoredTags) {
                if (scoredTag.getTagId() == morphPosId.getSecond()) {
                    LatticeNode firstIrregularNode = this.makeNode(beginIdx, endIdx, morphPosId.getFirst(), scoredTag.getTag(), scoredTag.getTagId(), score + scoredTag.getScore(), maxTransitionPrevIdx);
                    this.appendNode(firstIrregularNode);
                    //????????? ????????? EC??? ???????????? EF??? ???????????? ????????? ????????????
                    if (scoredTag.getTagId() == SEJONGTAGS.EC_ID) {
                        LatticeNode extendIrregularNode = this.makeNode(beginIdx, endIdx, morphPosId.getFirst(), SYMBOL.EF, this.posTable.getId(SYMBOL.EF), score + scoredTag.getScore(), maxTransitionPrevIdx);
                        this.appendNode(extendIrregularNode);
                    }
                }
            }
        } else {
            Pair<String, Integer> morphPosId = irregularTokens.get(0);
            List<ScoredTag> scoredTags = this.observation.getTrieDictionary().getValue(morphPosId.getFirst());
            for (ScoredTag scoredTag : scoredTags) {
                if (scoredTag.getTagId() == morphPosId.getSecond()) {
                    LatticeNode firstIrregularNode = this.makeNode(beginIdx, irrIdx - 1, morphPosId.getFirst(), scoredTag.getTag(), scoredTag.getTagId(), score + scoredTag.getScore(), maxTransitionPrevIdx);
                    irrIdx--;
                    this.appendNode(firstIrregularNode);
                }
            }

        }
    }
*/
    public boolean put(int beginIdx, int endIdx, String morph, String tag, int tagId, double score) {

        List<LatticeNode> prevLatticeNodes = this.getNodeList(beginIdx);

        if (prevLatticeNodes != null) {
            if (nbest != 1) {
                List<LatticeNode> nbestLatticeNodeList = this.getNbestMaxTransitionNodeFromPrevNodes(prevLatticeNodes, beginIdx, endIdx, morph, tag, tagId, score, this.nbest);

                if (nbestLatticeNodeList != null) {
                    for (LatticeNode latticeNode : nbestLatticeNodeList) {
                        this.appendNode(latticeNode);
                    }
                    return true;
                }
            } else {
                LatticeNode maxLatticeNode = this.getMaxTransitionNodeFromPrevNodes(prevLatticeNodes, beginIdx, endIdx, morph, tag, tagId, score);
                if (maxLatticeNode != null) {
                    this.appendNode(maxLatticeNode);
                    return true;
                }
            }

        }
        return false;
    }

    private List<LatticeNode> getNbestMaxTransitionNodeFromPrevNodes(
            List<LatticeNode> prevLatticeNodes, int beginIdx, int endIdx,
            String morph, String tag, int tagId, double score, int nbest) {

        List<LatticeNode> nbestPrevNodeList = new ArrayList<>();
        int latticeNodeIdx = -1;
        for (LatticeNode prevLatticeNode : prevLatticeNodes) {
            latticeNodeIdx++;
            //??????????????????
            if (prevLatticeNode.getMorphTag().getTagId() == -1) {
                continue;
            }
            int prevTagId;
            String prevMorph;
            if (prevLatticeNode.getMorphTag().getTag().equals(SYMBOL.EOE)) {
                prevTagId = SEJONGTAGS.BOE_ID;
                prevMorph = SYMBOL.BOE;
            } else {
                prevTagId = prevLatticeNode.getMorphTag().getTagId();
                prevMorph = prevLatticeNode.getMorphTag().getMorph();
            }
            //?????? ?????? ??? ?????????
            Double transitionScore = this.transition.get(prevTagId, tagId);
            if (transitionScore == null) {
                continue;
            }

            //???????????? ??????
            if (!isValidCombination(prevMorph, prevTagId, morph, tagId)) {
                continue;
            }


            double prevObservationScore = prevLatticeNode.getScore();

            if (nbestPrevNodeList.size() < nbest) {
                nbestPrevNodeList.add(
                        this.makeNode(beginIdx, endIdx, morph, tag, tagId, transitionScore + prevObservationScore + score, latticeNodeIdx)
                );
                continue;
            }

            int nbestMinIndex = 0;
            double nbestMinScore = nbestPrevNodeList.get(0).getScore();

            for (int i = 1; i < nbestPrevNodeList.size(); i++) {
                if (nbestMinScore > nbestPrevNodeList.get(i).getScore()) {
                    nbestMinIndex = i;
                    nbestMinScore = nbestPrevNodeList.get(i).getScore();
                }
            }

            if (nbestMinScore < transitionScore + prevObservationScore + score) {
                nbestPrevNodeList.set(
                        nbestMinIndex,
                        this.makeNode(beginIdx, endIdx, morph, tag, tagId, transitionScore + prevObservationScore + score, latticeNodeIdx)
                );
            }
        }
        if (nbestPrevNodeList.size() != 0) {
            return nbestPrevNodeList;
        }
        return null;
    }

    private boolean isValidCombination(String prevMorph, int prevTagId, String morph, int tagId) {
        return this.combinationRuleChecker.isValidRule(prevMorph, prevTagId, morph, tagId);
    }

    private LatticeNode getMaxTransitionNodeFromPrevNodes(
            List<LatticeNode> prevLatticeNodes, int beginIdx, int endIdx,
            String morph, String tag, int tagId, double score) {

        double prevMaxScore = Double.NEGATIVE_INFINITY;
        LatticeNode prevMaxNode = null;
        int latticeNodeIdx = -1;
        int prevLatticeNodeIdx = -1;
        for (LatticeNode prevLatticeNode : prevLatticeNodes) {
            latticeNodeIdx++;
            //??????????????????
            if (prevLatticeNode.getMorphTag().getTagId() == -1) {
                continue;
            }
            int prevTagId;
            String prevMorph;
            if (prevLatticeNode.getMorphTag().getTag().equals(SYMBOL.EOE)) {
                prevTagId = SEJONGTAGS.BOE_ID;
                prevMorph = SYMBOL.BOE;
            } else {
                prevTagId = prevLatticeNode.getMorphTag().getTagId();
                prevMorph = prevLatticeNode.getMorphTag().getMorph();
            }
            //?????? ?????? ??? ?????????
            Double transitionScore = this.transition.get(prevTagId, tagId);
            if (transitionScore == null) {
                continue;
            }

            //???????????? ??????
            if (!isValidCombination(prevMorph, prevTagId, morph, tagId)) {
                continue;
            }

            double prevObservationScore = prevLatticeNode.getScore();

            if (prevMaxScore < transitionScore + prevObservationScore) {
                prevMaxScore = transitionScore + prevObservationScore;
                prevMaxNode = prevLatticeNode;
                prevLatticeNodeIdx = latticeNodeIdx;
            }
        }
        if (prevMaxNode != null) {
            return this.makeNode(beginIdx, endIdx, morph, tag, tagId, prevMaxScore + score, prevLatticeNodeIdx);
        }
        return null;
    }

    public LatticeNode makeNode(int beginIdx, int endIdx, String morph,
                                String tag, int tagId, double score, int prevNodeHash) {
        LatticeNode latticeNode = new LatticeNode(beginIdx, endIdx, new MorphTag(morph, tag, tagId), score);
        latticeNode.setPrevNodeIdx(prevNodeHash);
        return latticeNode;
    }

    public int appendNode(LatticeNode latticeNode) {
        List<LatticeNode> latticeNodeList = this.getNodeList(latticeNode.getEndIdx());
        if (latticeNodeList == null) {
            latticeNodeList = new ArrayList<>();
        }
        latticeNodeList.add(latticeNode);
        this.lattice.put(latticeNode.getEndIdx(), latticeNodeList);
        return latticeNodeList.size() - 1;
    }

    public List<LatticeNode> getNodeList(int index) {
        return this.lattice.get(index);
    }

    private void getMaxTransitionIdxFromPrevNodes(List<LatticeNode> prevLatticeNodes, int tagId) {
        this.getMaxTransitionInfoFromPrevNodes(prevLatticeNodes, tagId);
    }

    private void getMaxTransitionInfoFromPrevNodes(List<LatticeNode> prevLatticeNodes, int tagId) {

        int prevMaxNodeIdx = -1;
        for (LatticeNode prevLatticeNode : prevLatticeNodes) {
            prevMaxNodeIdx++;
            //??????????????????
            if (prevLatticeNode.getMorphTag().getTagId() == -1) {
                continue;
            }
            int prevTagId;
            if (prevLatticeNode.getMorphTag().getTag().equals(SYMBOL.EOE)) {
                prevTagId = SEJONGTAGS.BOE_ID;
            } else {
                prevTagId = prevLatticeNode.getMorphTag().getTagId();
            }
            //?????? ?????? ??? ?????????
            Double transitionScore = this.transition.get(prevTagId, tagId);
            if (transitionScore == null) {
                continue;
            }

            double prevObservationScore = prevLatticeNode.getScore();

            if (this.prevMaxScore < transitionScore + prevObservationScore) {
                this.prevMaxScore = transitionScore + prevObservationScore;
                this.prevMaxNode = prevLatticeNode;
                this.prevMaxIdx = prevMaxNodeIdx;
            }
        }
    }

    public void setPosTable(PosTable posTable) {
        this.posTable = posTable;
    }

    public void setTransition(Transition transition) {
        this.transition = transition;
    }

    public void printLattice() {
        int totalLatticeSize = 0;
        for (int i = irrIdx; i < this.getLastIdx() + 2; i++) {
            System.out.println("[" + i + "]");
            List<LatticeNode> nodeList = this.lattice.get(i);
            if (nodeList == null) {
                continue;
            }
            totalLatticeSize += nodeList.size();
            int nodeIndex = 0;
            for (LatticeNode latticeNode : nodeList) {
                System.out.println(nodeIndex + " : "+latticeNode);
                nodeIndex++;
            }
            System.out.println();
        }
        System.out.println("Total lattice size : " + totalLatticeSize);
    }

    public int getLastIdx() {
        return lastIdx;
    }

    public void setLastIdx(int lastIdx) {
        this.lastIdx = lastIdx;
    }

    public boolean appendEndNode() {
        return this.put(this.lastIdx, this.lastIdx + 1, SYMBOL.EOE, SYMBOL.EOE, SEJONGTAGS.EOE_ID, 0);
    }

    public List<LatticeNode> findPath() {
        List<LatticeNode> shortestPathList = new ArrayList<>();
        int idx = this.getLastIdx() + 1;
        //????????? ?????? ????????? ?????? ???????????? null ??????
        if (!this.lattice.containsKey(idx)) {
            return null;
        }

        LatticeNode latticeNode = this.lattice.get(idx).get(0);

        int prevLatticeEndIndex = latticeNode.getEndIdx();
        while (true) {
            latticeNode = this.lattice.get(latticeNode.getBeginIdx()).get(latticeNode.getPrevNodeIdx());
            if (latticeNode.getEndIdx() < 0) {
                latticeNode.setEndIdx(prevLatticeEndIndex);
            }
            shortestPathList.add(latticeNode);
            prevLatticeEndIndex = latticeNode.getEndIdx();
            if (latticeNode.getBeginIdx() == 0) {
                break;
            }
        }

        return shortestPathList;
    }

/*
    private void putIrregularTokens(int beginIdx, int endIdx, List<Pair<String, Integer>> morphPosIdList) {

        for (int i = 1; i < morphPosIdList.size(); i++) {
            Pair<String, Integer> morphPosId = morphPosIdList.get(i);
            List<ScoredTag> scoredTags = this.observation.getTrieDictionary().getValue(morphPosId.getFirst());
            if (i == morphPosIdList.size() - 1) {
                for (ScoredTag scoredTag : scoredTags) {
                    if (scoredTag.getTagId() == morphPosId.getSecond()) {
                        this.put(irrIdx, endIdx, morphPosId.getFirst(), this.posTable.getPos(morphPosId.getSecond()), morphPosId.getSecond(), scoredTag.getScore());
                        if (morphPosId.getSecond() == SEJONGTAGS.EC_ID) {
                            this.put(irrIdx, endIdx, morphPosId.getFirst(), SYMBOL.EF, SEJONGTAGS.EF_ID, scoredTag.getScore());
                        }
                    }
                }
            } else {
                for (ScoredTag scoredTag : scoredTags) {
                    if (scoredTag.getTagId() == morphPosId.getSecond()) {
                        this.put(irrIdx, irrIdx - 1, morphPosId.getFirst(), this.posTable.getPos(morphPosId.getSecond()), morphPosId.getSecond(), scoredTag.getScore());
                    }
                }
            }
            irrIdx--;
        }
    }
*/
    public void setObservation(Observation observation) {
        this.observation = observation;
    }

    public List<List<LatticeNode>> findNBestPath() {
        List<List<LatticeNode>> nBestShortestPathList = new ArrayList<>();
        int idx = this.getLastIdx() + 1;
        //????????? ?????? ????????? ?????? ???????????? null ??????
        if (!this.lattice.containsKey(idx)) {
            return null;
        }

        for (LatticeNode endNode : this.lattice.get(idx)) {
            List<LatticeNode> shortestPathList = new ArrayList<>();
            int prevLatticeEndIndex = endNode.getEndIdx();
            LatticeNode latticeNode = endNode;
            shortestPathList.add(latticeNode);
            while (true) {
                latticeNode = this.lattice.get(latticeNode.getBeginIdx()).get(latticeNode.getPrevNodeIdx());
                //?????????????????? multi token ????????? ????????? ??????
                if (latticeNode.getEndIdx() < 0) {
                    latticeNode.setEndIdx(prevLatticeEndIndex);
                }
                shortestPathList.add(latticeNode);
                prevLatticeEndIndex = latticeNode.getEndIdx();
                if (latticeNode.getBeginIdx() == 0) {
                    break;
                }
            }

            nBestShortestPathList.add(shortestPathList);

        }

        if (nBestShortestPathList.size() > 1) {
            nBestShortestPathList = sortNBestByScore(nBestShortestPathList);
        }

        return nBestShortestPathList;
    }

    private List<List<LatticeNode>> sortNBestByScore(List<List<LatticeNode>> nBestShortestPathList) {
        Map<List<LatticeNode>, Double> sortedLatticeNodeList = new HashMap<>();
        for (List<LatticeNode> latticeNodes : nBestShortestPathList) {
            double score = latticeNodes.get(0).getScore();
            sortedLatticeNodeList.put(latticeNodes, score);
        }

        return new ArrayList<>(MapUtil.sortByValue(sortedLatticeNodeList, MapUtil.DESCENDING_ORDER).keySet());
    }
}
