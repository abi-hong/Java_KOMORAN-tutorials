package kr.co.shineware.nlp.komoran.tutorials;

import kr.co.shineware.nlp.komoran.constant.DEFAULT_MODEL;
import kr.co.shineware.nlp.komoran.core.Komoran;
import kr.co.shineware.nlp.komoran.model.KomoranResult;
import kr.co.shineware.nlp.komoran.model.Token;

import java.util.List;

public class App {
    public static void main(String[] args) {
        // EXPERIMENT 모델을 갖는 Komoran 객체를 선언
        Komoran komoran = new Komoran(DEFAULT_MODEL.EXPERIMENT);
        String strToAnalyze = "대한민국은 민주공화국이다.";
        //String strToAnalyze = "밀리언 달러 베이비랑 바람과 함께 사라지다랑 뭐가 더 재밌었어?";

        // Komoran 객체의 analyze()메소드의 인자로 분석할 문장을 전달
        // 이 결과를 KomoranResult 객체로 저장
        KomoranResult analyzeResultList = komoran.analyze(strToAnalyze);

        // getPlainText() : 형태소 분석 결과가 태킹된 문장 형태를 받아봄
        System.out.println("==========print 'getPlainText()'==========");
        System.out.println(analyzeResultList.getPlainText());
        System.out.println();

        // 형태소 분석 결과 중 명사류를 List<String> 형태로 반환
        System.out.println("==========print 'getNouns()'==========");
        System.out.println(analyzeResultList.getNouns());
        System.out.println();

        // 형태소 분석 결과 중 용언류를 List<String> 형태로 반환
        System.out.println("==========print 'getVerbs()'==========");
        System.out.println(analyzeResultList.getVerbs());
        System.out.println();


        // 형태소 분석 결과 중 관계언류를 List<String> 형태로 반환
        System.out.println("==========print 'getRelatives()'==========");
        System.out.println(analyzeResultList.getRelatives());
        System.out.println();

        // 형태소 분석 결과 중 기호류를 List<String> 형테로 반환
        System.out.println("==========print 'getDependences()'==========");
        System.out.println(analyzeResultList.getDependences());
        System.out.println();

        // 형태소 분석 결과 중 명사류를 List<String> 형태로 반환
        System.out.println("==========print 'getSigns()'==========");
        System.out.println(analyzeResultList.getSigns());
        System.out.println();


        // 형태소 분석 결과 중 특정 품사에 해당하는 형태소를 List<String> 형태로 반환
        System.out.println("==========print 'getMorphesByTags()'==========");
        System.out.println(analyzeResultList.getMorphesByTags());
        System.out.println();

        // getTokenList() : 각 형태소(token)를 원소로 갖는 list로 받아봄
        // Token 객체 : 형태소, 품사 그리고 시작/끝 지점을 갖는 객체
        List<Token> tokenList = analyzeResultList.getTokenList();
        System.out.println("==========print 'getTokenList()'==========");
        for (Token token : tokenList) {
            System.out.format("(%2d, %2d) %s/%s\n", token.getBeginIndex(), token.getEndIndex(), token.getMorph(), token.getPos());
        }
        System.out.println();

        System.out.println("==========print 'getList()'==========");
        System.out.println(analyzeResultList.getList());

    }
}