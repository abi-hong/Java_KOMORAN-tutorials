/*******************************************************************************
 * KOMORAN 3.0 - Korean Morphology Analyzer
 *
 * Copyright 2015 Shineware http://www.shineware.co.kr
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package kr.co.shineware.nlp.komoran.core;

import kr.co.shineware.ds.aho_corasick.FindContext;
import kr.co.shineware.nlp.komoran.constant.*;
import kr.co.shineware.nlp.komoran.core.model.*;
import kr.co.shineware.nlp.komoran.core.model.combinationrules.CombinationRuleChecker;
import kr.co.shineware.nlp.komoran.core.model.combinationrules.MergedCombinationRuleChecker;
import kr.co.shineware.nlp.komoran.corpus.parser.CorpusParser;
import kr.co.shineware.nlp.komoran.corpus.parser.model.ProblemAnswerPair;
import kr.co.shineware.nlp.komoran.model.KomoranResult;
import kr.co.shineware.nlp.komoran.model.MorphTag;
import kr.co.shineware.nlp.komoran.model.ScoredTag;
import kr.co.shineware.nlp.komoran.modeler.model.IrregularNode;
import kr.co.shineware.nlp.komoran.modeler.model.Observation;
import kr.co.shineware.nlp.komoran.parser.KoreanUnitParser;
import kr.co.shineware.nlp.komoran.util.KomoranCallable;
import kr.co.shineware.util.common.file.FileUtil;
import kr.co.shineware.util.common.model.Pair;
import kr.co.shineware.util.common.string.StringUtil;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * KOMORAN core ??????????????????.
 */
public class Komoran implements Cloneable {

    private CombinationRuleChecker combinationRuleChecker;
    private Resources resources;
    private Observation userDic;
    private KoreanUnitParser unitParser;

    private HashMap<String, List<Pair<String, String>>> fwd;

    /**
     * modelPath ??????????????? ?????? ?????? ???????????? ???????????? ????????? ???????????????. </p>
     * modelPath ?????????????????? pos.table, observation.model, transition.model, irregular.model ????????? ???????????? ????????? ?????????. </p>
     * ??? ????????? ModelBuilder??? ?????? ???????????????.
     *
     * @param modelPath ?????? ???????????? ???????????? ?????? ???????????? ??????
     */
    public Komoran(String modelPath) {
        this.resources = new Resources();
        this.load(modelPath);
        this.unitParser = new KoreanUnitParser();
        MorphUtil morphUtil = new MorphUtil();
        TagUtil tagUtil = new TagUtil(this.resources.getTable());
        this.combinationRuleChecker = new MergedCombinationRuleChecker(morphUtil, tagUtil);
    }

    /**
     * Komoran?????? ???????????? ???????????? ????????? ???????????? ????????? ???????????????. </p>
     * ????????? ????????? ????????? ????????? ????????????.
     *
     * @param modelType ???????????? ???????????? ????????? ??????
     */
    public Komoran(DEFAULT_MODEL modelType) {

        this.resources = new Resources();
        this.resources.init();
        String modelPath;
        if (modelType == DEFAULT_MODEL.EXPERIMENT) {
            modelPath = FILENAME.EXPERIMENT_MODEL;
        } else if (modelType == DEFAULT_MODEL.STABLE) {
            modelPath = FILENAME.STABLE_MODEL;
        } else {
            modelPath = FILENAME.STABLE_MODEL;
        }

        String delimiter = "/";
        InputStream posTableFile =
                this.getResourceStream(modelPath + delimiter + FILENAME.POS_TABLE);
        InputStream irrModelFile =
                this.getResourceStream(modelPath + delimiter + FILENAME.IRREGULAR_MODEL);
        InputStream observationFile =
                this.getResourceStream(modelPath + delimiter + FILENAME.OBSERVATION);
        InputStream transitionFile =
                this.getResourceStream(modelPath + delimiter + FILENAME.TRANSITION);

        this.resources.loadPosTable(posTableFile);
        this.resources.loadIrregular(irrModelFile);
        this.resources.loadObservation(observationFile);
        this.resources.loadTransition(transitionFile);
        this.unitParser = new KoreanUnitParser();

        MorphUtil morphUtil = new MorphUtil();
        TagUtil tagUtil = new TagUtil(this.resources.getTable());
        this.combinationRuleChecker = new MergedCombinationRuleChecker(morphUtil, tagUtil);
    }

    private InputStream getResourceStream(String path) {
        return getClass().getClassLoader().getResourceAsStream(path);
    }

    /**
     * ?????? ????????? ????????? ????????? ???????????????.
     *
     * @param inputFilename  ????????? ?????? ??????
     * @param outputFilename ?????? ????????? ????????? ?????? ??????
     * @param thread         ?????? ??? ????????? thread ???
     */
    public void analyzeTextFile(String inputFilename, String outputFilename, int thread) {

        try {
            List<String> lines = FileUtil.load2List(inputFilename);

            BufferedWriter bw = new BufferedWriter(
                    (new OutputStreamWriter(new FileOutputStream(outputFilename), StandardCharsets.UTF_8)));
            List<Future<KomoranResult>> komoranResultList = new ArrayList<>();
            ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(thread);

            for (String line : lines) {
                KomoranCallable komoranCallable = new KomoranCallable(this, line);
                komoranResultList.add(executor.submit(komoranCallable));
            }

            for (Future<KomoranResult> komoranResultFuture : komoranResultList) {
                KomoranResult komoranResult = komoranResultFuture.get();
                bw.write(komoranResult.getPlainText());
                bw.newLine();
            }
            bw.close();
            executor.shutdown();

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    /**
     * ?????? ????????? ?????? ?????? ????????? ????????? ???????????????.
     *
     * @param sentences ????????? ???????????? ?????? List. ??? ????????? ????????? ??????????????? ???????????????.
     * @param thread    ?????? ??? ????????? thread ???
     * @return ?????? ??? ????????? ?????? ????????? ?????? List
     */
    public List<KomoranResult> analyze(List<String> sentences, int thread) {

        List<KomoranResult> komoranResultList = new ArrayList<>();

        try {
            List<Future<KomoranResult>> komoranResultFutureList = new ArrayList<>();
            ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(thread);

            for (String line : sentences) {
                KomoranCallable komoranCallable = new KomoranCallable(this, line);
                komoranResultFutureList.add(executor.submit(komoranCallable));
            }

            for (Future<KomoranResult> komoranResultFuture : komoranResultFutureList) {
                komoranResultList.add(komoranResultFuture.get());
            }
            executor.shutdown();

        } catch (Exception e) {
            e.printStackTrace();
        }

        return komoranResultList;
    }

    /**
     * ????????? ????????? ????????? ????????? ????????? ???????????????.
     *
     * @param sentence ?????? ?????? ??????
     * @return ????????? ?????? ??????
     */
    public KomoranResult analyze(String sentence) {
        return this.analyze(sentence, 1).get(0);
    }

    public double scoreDebug(List<String> analyzeResultList, List<String> analyzePosList){

        String prevPos = SYMBOL.BOE;
        double score = 0.0;

        for(int i=0;i<analyzePosList.size();i++){
            String currentMorph = analyzeResultList.get(i);
            String currentPos = analyzePosList.get(i);
            FindContext<List<ScoredTag>> findContext = this.resources.getObservation().getTrieDictionary().newFindContext();
            String jasoUnits = unitParser.parse(currentMorph);
            ScoredTag scoredTag = getScoredTag(jasoUnits, findContext, currentPos);
            score += getScore(currentMorph, scoredTag, prevPos, currentPos);
            prevPos = currentPos;
        }
        return score;
    }

    private double getScore(String currentMorph, ScoredTag scoredTag, String prevPos, String currentPos) {
        int prevId = this.resources.getTable().getId(prevPos);
        int currentId= this.resources.getTable().getId(currentPos);
        double transitionScore = this.resources.getTransition().get(prevId, currentId);
        System.out.println(prevPos+"->"+currentPos+":"+transitionScore);
        System.out.println(currentMorph+ " : "+scoredTag);

        return transitionScore + scoredTag.getScore();

    }

    private ScoredTag getScoredTag(String jasoUnits, FindContext<List<ScoredTag>> findContext, String posResult) {

        for(int i=0;i<jasoUnits.length();i++){
            Map<String, List<ScoredTag>> keyScoreTagMap = this.resources.getObservation().getTrieDictionary().get(findContext, jasoUnits.charAt(i));
            if(keyScoreTagMap.get(jasoUnits) == null){
                continue;
            }
            for (ScoredTag scoredTag : keyScoreTagMap.get(jasoUnits)) {
                if(scoredTag.getTag().equals(posResult)){
                    return scoredTag;
                }
            }
        }
        return null;
    }

    /**
     * ????????? ????????? ????????? ????????? ????????? ?????? ??? n-best ????????? ???????????????.
     *
     * @param sentence ?????? ?????? ??????
     * @param nbest    ?????? ?????? ??? ????????? ?????? n?????? ???
     * @return ????????? ?????? ?????? ??? nbest ??? ????????? ??????
     */
    public List<KomoranResult> analyze(String sentence, int nbest) {

        if(sentence == null || sentence.length() == 0){
            return new ArrayList<>(
                    Collections.singletonList(new KomoranResult(new ArrayList<>(), ""))
            );
        }

        Lattice lattice = new Lattice(this.resources, this.userDic, nbest, combinationRuleChecker);

        //????????? ??????, ?????????, ?????? ?????? ?????? ?????? ?????? ??????
        ContinuousSymbolBuffer continuousSymbolBuffer = new ContinuousSymbolBuffer();

        //?????? ????????? ??????
        String jasoUnits = unitParser.parse(sentence);
        List<Pair<Character, KoreanUnitParser.UnitType>> jasoUnitsWithType = unitParser.parseWithType(sentence);

        int length = jasoUnits.length();
        //start ?????? ?????? end ????????? ?????? ?????? ?????????
        //????????? ????????? ????????? idx
        int whitespaceIndex = 0;
        boolean inserted;

        for (int curJasoIndex = 0; curJasoIndex < length; curJasoIndex++) {

            //????????? ??????
            int skipIdx = this.lookupFwd(lattice, jasoUnits, curJasoIndex);
            if (skipIdx != -1) {
                curJasoIndex = skipIdx - 1;
                continue;
            }

            //??????????????? ??????
            if (jasoUnits.charAt(curJasoIndex) == ' ') {
                this.consumeContiniousSymbolParserBuffer(lattice, curJasoIndex, continuousSymbolBuffer);
                this.bridgeToken(lattice, curJasoIndex, jasoUnits, whitespaceIndex, jasoUnitsWithType);
                whitespaceIndex = curJasoIndex + 1;
            }

            //??? ????????? ?????? ??? ????????? ???????????? ?????? ??? ??? ????????? ???????????????
            this.continuousSymbolParsing(lattice, jasoUnits.charAt(curJasoIndex), curJasoIndex, continuousSymbolBuffer); //??????, ??????, ????????? ??????

            //?????? ????????? ??????
            this.symbolParsing(lattice, jasoUnits.charAt(curJasoIndex), curJasoIndex); // ?????? ?????? ??????
            this.userDicParsing(lattice, jasoUnits.charAt(curJasoIndex), curJasoIndex); //????????? ?????? ??????

            this.regularParsing(lattice, jasoUnits.charAt(curJasoIndex), curJasoIndex); //???????????? ??????
            this.irregularParsing(lattice, jasoUnits.charAt(curJasoIndex), curJasoIndex); //????????? ??????
            this.irregularExtends(lattice, jasoUnits.charAt(curJasoIndex), curJasoIndex); //????????? ??????
        }


        this.consumeContiniousSymbolParserBuffer(lattice, jasoUnits, continuousSymbolBuffer);
        lattice.setLastIdx(jasoUnits.length());
        inserted = lattice.appendEndNode();
        //?????? ????????? ?????? END ????????? ??? ??? ?????? ??????
        if (!inserted) {
            double NAPenaltyScore = SCORE.NA;
            if (whitespaceIndex != 0) {
                NAPenaltyScore += lattice.getNodeList(whitespaceIndex).get(0).getScore();
            }
            String combinedWord = unitParser.combineWithType(jasoUnitsWithType.subList(whitespaceIndex, jasoUnits.length()));
            LatticeNode latticeNode = new LatticeNode(whitespaceIndex, jasoUnits.length(), new MorphTag(combinedWord, SYMBOL.NA, SEJONGTAGS.NA_ID), NAPenaltyScore);
            latticeNode.setPrevNodeIdx(0);
            lattice.appendNode(latticeNode);
            lattice.appendEndNode();
        }

        List<List<LatticeNode>> nBestPath = lattice.findNBestPath();

        List<KomoranResult> nbestResultList = new ArrayList<>();

        //?????? ?????? ????????? ???????????? ??????
        if (nBestPath == null) {
            List<LatticeNode> resultList = new ArrayList<>();
            resultList.add(new LatticeNode(0, jasoUnits.length(), new MorphTag(sentence, "NA", -1), SCORE.NA));
            nbestResultList.add(new KomoranResult(resultList, jasoUnits));
        } else {
            for (List<LatticeNode> shortestPath : nBestPath) {
                Collections.reverse(shortestPath);
                List<LatticeNode> resultList = new ArrayList<>(shortestPath);
                nbestResultList.add(new KomoranResult(resultList, jasoUnits));
            }
        }

        return nbestResultList;
    }


    private void bridgeToken(Lattice lattice, int curIdx, String jasoUnits, int prevBeginSymbolIdx, List<Pair<Character, KoreanUnitParser.UnitType>> jasoUnitsWithType) {


        if (lattice.put(curIdx, curIdx + 1, SYMBOL.EOE, SYMBOL.EOE, SEJONGTAGS.EOE_ID, 0.0)) {
            return;
        }
        //??????????????? END ????????? ??????
        LatticeNode naLatticeNode = lattice.makeNode(prevBeginSymbolIdx, curIdx, unitParser.combineWithType(jasoUnitsWithType.subList(prevBeginSymbolIdx, curIdx)), SYMBOL.NA, this.resources.getTable().getId(SYMBOL.NA), SCORE.NA, 0);

        int naNodeIndex = lattice.appendNode(naLatticeNode);
        LatticeNode endLatticeNode = lattice.makeNode(curIdx, curIdx + 1, SYMBOL.EOE, SYMBOL.EOE, SEJONGTAGS.EOE_ID, 0.0, naNodeIndex);
        lattice.appendNode(endLatticeNode);
    }

    private void symbolParsing(Lattice lattice, char jaso, int idx) {

        Character.UnicodeBlock unicodeBlock = Character.UnicodeBlock.of(jaso);
        //??????
        if (StringUtil.isNumeric(jaso)) {
        } else if (unicodeBlock == Character.UnicodeBlock.BASIC_LATIN) {
            if (!isEnglishCharacter(jaso) && !isWhitespaceCharacter(jaso) && !isDictionaryEntryCharacter(jaso)) {
                lattice.put(idx, idx + 1, "" + jaso, SYMBOL.SW, SEJONGTAGS.SW_ID, SCORE.SW);
            }
        } else if (!StringUtil.isKorean(jaso) && !StringUtil.isJapanese(jaso) && !StringUtil.isChinese(jaso)) {
            lattice.put(idx, idx + 1, "" + jaso, SYMBOL.SW, SEJONGTAGS.SW_ID, SCORE.SW);
        }
    }

    private boolean isDictionaryEntryCharacter(char jaso) {
        return this.resources.getObservation().getTrieDictionary().getValue("" + jaso) != null;
    }

    private boolean isWhitespaceCharacter(char jaso) {
        return jaso == ' ';
    }

    private boolean isEnglishCharacter(char jaso) {
        return ((jaso >= 'A') && (jaso <= 'Z')) || ((jaso >= 'a') && (jaso <= 'z'));
    }

    private void userDicParsing(Lattice lattice, char jaso, int curIndex) {
        //Aho-corasick TRIE ????????? ?????? ???????????? ???????????? ?????? ??? ?????? ??????(observation)??? ?????????
        Map<String, List<ScoredTag>> morphScoredTagsMap = lattice.retrievalUserDicObservation(jaso);

        if (morphScoredTagsMap == null || morphScoredTagsMap.size() == 0) {
            return;
        }

        //????????? ????????? ?????????
        Set<String> morphes = morphScoredTagsMap.keySet();

        //??? ???????????? ?????? ????????? lattice??? ??????
        for (String morph : morphes) {
            int beginIdx = curIndex - morph.length() + 1;
            int endIdx = curIndex + 1;

            //???????????? ?????? ?????? ??? ??????(observation) ????????? List ????????? ?????????
            List<ScoredTag> scoredTags = morphScoredTagsMap.get(morph);
            for (ScoredTag scoredTag : scoredTags) {
                lattice.put(beginIdx, endIdx, morph, scoredTag.getTag(), scoredTag.getTagId(), scoredTag.getScore());
            }
        }
    }

    private int lookupFwd(Lattice lattice, String token, int curJasoIndex) {

        if (this.fwd == null) {
            return -1;
        }

        //?????? ???????????? ??????????????? ?????? ???????????? ????????? ?????? (word ????????? ??????)
        //???, ?????? ???????????? ????????? ????????? ?????? ????????? ??????
        if (curJasoIndex == 0 || token.charAt(curJasoIndex - 1) == ' ') {
            //?????? ????????? ?????? ??????(word)??? ????????? ???????????? ?????????
            //????????? ????????? ????????? ?????? ???????????? ????????? ???????????? ????????? = ????????? ????????? ??????
            int wordEndIdx = token.indexOf(' ', curJasoIndex);
            wordEndIdx = wordEndIdx == -1 ? token.length() : wordEndIdx;
            String targetWord = token.substring(curJasoIndex, wordEndIdx);
            List<Pair<String, String>> fwdResultList = this.fwd.get(targetWord);

            if (fwdResultList != null) {
                this.insertLatticeForFwd(lattice, curJasoIndex, wordEndIdx, fwdResultList, targetWord);
                return wordEndIdx;
            }
        }
        return -1;
    }

    private void insertLatticeForFwd(Lattice lattice, int beginIdx, int endIdx,
                                     List<Pair<String, String>> fwdResultList, String targetWord) {

        //????????? ????????? targetWord??? ???????????? ???????????? ??????
//        if (hasRegularFWDValues(fwdResultList, targetWord)) {
//            for (Pair<String, String> morphPosPair : fwdResultList) {
//                lattice.put(beginIdx, beginIdx + this.unitParser.parse(morphPosPair.getFirst()).length(), morphPosPair.getFirst(), morphPosPair.getSecond(), this.resources.getTable().getId(morphPosPair.getSecond()), 0.0);
//                beginIdx += beginIdx + this.unitParser.parse(morphPosPair.getFirst()).length();
//            }
//        } else {
        lattice.put(beginIdx, endIdx, fwdResultList);
//        }
    }

    private boolean hasRegularFWDValues(List<Pair<String, String>> fwdResultList, String targetWord) {
        StringBuilder fwdMorphs = new StringBuilder();

        for (Pair<String, String> morphPosPair : fwdResultList) {
            fwdMorphs.append(this.unitParser.parse(morphPosPair.getFirst()));
        }

        return fwdMorphs.toString().equals(targetWord);
    }

    private void continuousSymbolParsing(Lattice lattice, char charAt, int i, ContinuousSymbolBuffer continuousSymbolBuffer) {
        String curPos = "";
        if (StringUtil.isEnglish(charAt)) {
            curPos = "SL";
        } else if (StringUtil.isNumeric(charAt)) {
            curPos = "SN";
        } else if (StringUtil.isChinese(charAt)) {
            curPos = "SH";
        } else if (StringUtil.isForeign(charAt)) {
            curPos = "SL";
        }

        //?????? ????????? ????????? ?????? ????????? ????????? ?????? ???????????? ???????????? ??????
        if (curPos.equals(continuousSymbolBuffer.getPrevPos())) {
            continuousSymbolBuffer.setPrevMorph(continuousSymbolBuffer.getPrevMorph() + charAt);
        } else {
            switch (continuousSymbolBuffer.getPrevPos()) {
                case "SL":
                    lattice.put(continuousSymbolBuffer.getPrevBeginIdx(), i,
                            continuousSymbolBuffer.getPrevMorph(),
                            continuousSymbolBuffer.getPrevPos(),
                            this.resources.getTable().getId(continuousSymbolBuffer.getPrevPos()),
                            SCORE.SL
                    );
                    break;
                case "SN":
                    lattice.put(continuousSymbolBuffer.getPrevBeginIdx(), i,
                            continuousSymbolBuffer.getPrevMorph(),
                            continuousSymbolBuffer.getPrevPos(),
                            this.resources.getTable().getId(continuousSymbolBuffer.getPrevPos()),
                            SCORE.SN
                    );
                    break;
                case "SH":
                    lattice.put(continuousSymbolBuffer.getPrevBeginIdx(), i,
                            continuousSymbolBuffer.getPrevMorph(),
                            continuousSymbolBuffer.getPrevPos(),
                            this.resources.getTable().getId(continuousSymbolBuffer.getPrevPos()),
                            SCORE.SH
                    );
                    break;
            }
            continuousSymbolBuffer.setPrevBeginIdx(i);
            continuousSymbolBuffer.setPrevMorph("" + charAt);
            continuousSymbolBuffer.setPrevPos(curPos);
        }
    }

    private void consumeContiniousSymbolParserBuffer(Lattice lattice, String in, ContinuousSymbolBuffer continuousSymbolBuffer) {
        if (continuousSymbolBuffer.getPrevPos().trim().length() != 0) {
            switch (continuousSymbolBuffer.getPrevPos()) {
                case "SL":
                    lattice.put(continuousSymbolBuffer.getPrevBeginIdx(),
                            in.length(),
                            continuousSymbolBuffer.getPrevMorph(),
                            continuousSymbolBuffer.getPrevPos(),
                            this.resources.getTable().getId(continuousSymbolBuffer.getPrevPos()),
                            SCORE.SL
                    );
                    break;
                case "SH":
                    lattice.put(continuousSymbolBuffer.getPrevBeginIdx(),
                            in.length(),
                            continuousSymbolBuffer.getPrevMorph(),
                            continuousSymbolBuffer.getPrevPos(),
                            this.resources.getTable().getId(continuousSymbolBuffer.getPrevPos()),
                            SCORE.SH
                    );
                    break;
                case "SN":
                    lattice.put(continuousSymbolBuffer.getPrevBeginIdx(),
                            in.length(),
                            continuousSymbolBuffer.getPrevMorph(),
                            continuousSymbolBuffer.getPrevPos(),
                            this.resources.getTable().getId(continuousSymbolBuffer.getPrevPos()),
                            SCORE.SN
                    );
                    break;
            }
        }
    }

    private void consumeContiniousSymbolParserBuffer(Lattice lattice, int endIdx, ContinuousSymbolBuffer continuousSymbolBuffer) {
        if (continuousSymbolBuffer.getPrevPos().trim().length() != 0) {
            switch (continuousSymbolBuffer.getPrevPos()) {
                case "SL":
                    lattice.put(continuousSymbolBuffer.getPrevBeginIdx(), endIdx, continuousSymbolBuffer.getPrevMorph(),
                            continuousSymbolBuffer.getPrevPos(), this.resources.getTable().getId(continuousSymbolBuffer.getPrevPos()), SCORE.SL);
                    break;
                case "SH":
                    lattice.put(continuousSymbolBuffer.getPrevBeginIdx(), endIdx, continuousSymbolBuffer.getPrevMorph(),
                            continuousSymbolBuffer.getPrevPos(), this.resources.getTable().getId(continuousSymbolBuffer.getPrevPos()), SCORE.SH);
                    break;
                case "SN":
                    lattice.put(continuousSymbolBuffer.getPrevBeginIdx(), endIdx, continuousSymbolBuffer.getPrevMorph(),
                            continuousSymbolBuffer.getPrevPos(), this.resources.getTable().getId(continuousSymbolBuffer.getPrevPos()), SCORE.SN);
                    break;
            }
        }
    }

    private void irregularExtends(Lattice lattice, char jaso, int curIndex) {
        List<LatticeNode> prevLatticeNodes = lattice.getNodeList(curIndex);
        if (prevLatticeNodes != null) {
            Set<LatticeNode> extendedIrrNodeList = new HashSet<>();

            for (LatticeNode prevLatticeNode : prevLatticeNodes) {
                //????????? ????????? ????????? ????????????
                if (prevLatticeNode.getMorphTag().getTagId() == SYMBOL.IRREGULAR_ID) {
                    //????????? ????????? ????????? ?????????
                    String lastMorph = prevLatticeNode.getMorphTag().getMorph();

                    //???????????? ????????? ???????????? ?????? ?????? ????????? ????????? ??? ?????? ????????? ????????? ?????? ?????? ?????? ????????? ?????? ?????????
                    if (this.resources.getObservation().getTrieDictionary().hasChild((lastMorph + jaso).toCharArray())) {
                        LatticeNode extendedIrregularNode = new LatticeNode();
                        extendedIrregularNode.setBeginIdx(prevLatticeNode.getBeginIdx());
                        extendedIrregularNode.setEndIdx(curIndex + 1);
                        extendedIrregularNode.setMorphTag(new MorphTag(prevLatticeNode.getMorphTag().getMorph() + jaso, SYMBOL.IRREGULAR, SYMBOL.IRREGULAR_ID));
                        extendedIrregularNode.setPrevNodeIdx(prevLatticeNode.getPrevNodeIdx());
                        extendedIrregularNode.setScore(prevLatticeNode.getScore());
                        extendedIrrNodeList.add(extendedIrregularNode);
                    }
                    //???????????? ????????? ???????????? ?????? ?????? ????????? ?????? ????????? ?????????
                    List<ScoredTag> lastScoredTags = this.resources.getObservation().getTrieDictionary().getValue(lastMorph + jaso);
                    if (lastScoredTags == null) {
                        continue;
                    }

                    //????????? ????????? ????????? lattice??? ??????
                    for (ScoredTag scoredTag : lastScoredTags) {
                        lattice.put(prevLatticeNode.getBeginIdx(), curIndex + 1, prevLatticeNode.getMorphTag().getMorph() + jaso,
                                scoredTag.getTag(), scoredTag.getTagId(), scoredTag.getScore());
                    }
                }
            }
            for (LatticeNode extendedIrrNode : extendedIrrNodeList) {
                lattice.appendNode(extendedIrrNode);
            }

        }
    }

    private void irregularParsing(Lattice lattice, char jaso, int curIndex) {
        //????????? ???????????? ?????????
        Map<String, List<IrregularNode>> morphIrrNodesMap = lattice.retrievalIrregularNodes(jaso);
        if (morphIrrNodesMap == null || morphIrrNodesMap.size() == 0) {
            return;
        }

        //????????? ????????? ?????????
        Set<String> morphs = morphIrrNodesMap.keySet();
        for (String morph : morphs) {

            int beginIdx = curIndex - morph.length() + 1;
            int endIdx = curIndex + 1;

            List<IrregularNode> irrNodes = morphIrrNodesMap.get(morph);
            for (IrregularNode irregularNode : irrNodes) {
//                lattice.put(beginIdx, endIdx, irregularNode);
                this.insertLattice(lattice, beginIdx, endIdx, irregularNode);
            }
        }
    }

    private void insertLattice(Lattice lattice, int beginIdx, int endIdx,
                               IrregularNode irregularNode) {
        lattice.put(beginIdx, endIdx, irregularNode);
    }

    private void regularParsing(Lattice lattice, char jaso, int curIndex) {
        //TRIE ????????? ?????? ???????????? ???????????? ?????? ??? ?????? ??????(observation)??? ?????????
        Map<String, List<ScoredTag>> morphScoredTagsMap = lattice.retrievalObservation(jaso);

        if (morphScoredTagsMap == null || morphScoredTagsMap.size() == 0) {
            return;
        }

        //????????? ????????? ?????????
        Set<String> morphes = morphScoredTagsMap.keySet();

        //??? ???????????? ?????? ????????? lattice??? ??????
        for (String morph : morphes) {
            int beginIdx = curIndex - morph.length() + 1;
            int endIdx = curIndex + 1;

            //???????????? ?????? ?????? ??? ??????(observation) ????????? List ????????? ?????????
            List<ScoredTag> scoredTags = morphScoredTagsMap.get(morph);
            for (ScoredTag scoredTag : scoredTags) {
                lattice.put(beginIdx, endIdx, morph, scoredTag.getTag(), scoredTag.getTagId(), scoredTag.getScore());
                //????????? EC??? ????????? ????????? EF??? ???????????? lattice??? ??????
                if (scoredTag.getTag().equals(SYMBOL.EC)) {
                    lattice.put(beginIdx, endIdx, morph, SYMBOL.EF, SEJONGTAGS.EF_ID, scoredTag.getScore());
                }
            }
        }
    }

    private void load(String modelPath) {
        this.resources.load(modelPath);
    }

    /**
     * ????????? ?????? ??? ????????? ????????? ????????? ???????????????. </p>
     * ????????? ?????? ?????? ?????? ??????????????? ?????????. </p>
     * <pre>
     *     Komoran komoran = new Komoran(DEFAULT_MODEL.STABLE);
     *     komoran.setFWDic("user_data/fwd.user");
     *     KomoranResult komoranResult = komoran.analyze("????????? ?????? ????????? ?????????");
     * </pre>
     *
     * @param filename ????????? ?????? ?????? ??????
     */
    public void setFWDic(String filename) {
        try {
            CorpusParser corpusParser = new CorpusParser();
            BufferedReader br = new BufferedReader(
                    new InputStreamReader(new FileInputStream(filename), StandardCharsets.UTF_8));
//            BufferedReader br = new BufferedReader(new FileReader(filename));
            String line;
            this.fwd = new HashMap<>();
            while ((line = br.readLine()) != null) {
                String[] tmp = line.split("\t");
                //??????????????? format??? ??? ?????? ????????? skip
                if (tmp.length != 2 || tmp[0].charAt(0) == '#') {
                    continue;
                }
                ProblemAnswerPair problemAnswerPair = corpusParser.parse(line);
                List<Pair<String, String>> convertAnswerList = new ArrayList<>();
                for (Pair<String, String> pair : problemAnswerPair.getAnswerList()) {
                    convertAnswerList.add(
                            new Pair<>(pair.getFirst(), pair.getSecond()));
                }

                this.fwd.put(this.unitParser.parse(problemAnswerPair.getProblem()),
                        convertAnswerList);
            }
            br.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * ????????? ?????? ??? ????????? ????????? ????????? ???????????????. </p>
     * ????????? ?????? ?????? ?????? ??????????????? ?????????.
     * <pre>
     *     Komoran komoran = new Komoran(DEFAULT_MODEL.STABLE);
     *     komoran.setUserDic("user_date/dic.user");
     *     KomoranResult komoranResult = komoran.analyze("????????? ?????? ??????????????? ??????");
     * </pre>
     *
     * @param userDic ????????? ?????? ?????? ??????
     */
    public void setUserDic(String userDic) {
        try {

            this.userDic = new Observation();
            BufferedReader br = new BufferedReader(new FileReader(userDic));
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.length() == 0 || line.charAt(0) == '#') continue;
                int lastIdx = line.lastIndexOf("\t");

                String morph;
                String pos;
                //????????? ????????? ????????? ?????? ???????????? ?????? ????????? ??????
                if (lastIdx == -1) {
                    morph = line.trim();
                    pos = "NNP";
                } else {
                    morph = line.substring(0, lastIdx);
                    pos = line.substring(lastIdx + 1);
                }
                this.userDic.put(morph, pos, this.resources.getTable().getId(pos), 0.0);

            }
            br.close();

            //init
            this.userDic.getTrieDictionary().buildFailLink();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
