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

        // Komoran 객체의 analyze()메소드의 인자로 분석할 문장을 전달
        // 이 결과를 KomoranResult 객체로 저장
        KomoranResult analyzeResultList = komoran.analyze(strToAnalyze);

        // getPlainText() : 형태소 분석 결과가 태킹된 문장 형태를 받아봄
        System.out.println(analyzeResultList.getPlainText());

        // getTokenList() : 각 형태소(token)를 원소로 갖는 list로 받아봄
        // Token 객체 : 형태소, 품사 그리고 시작/끝 지점을 갖는 객체
        List<Token> tokenList = analyzeResultList.getTokenList();
        for (Token token : tokenList) {
            System.out.format("(%2d, %2d) %s/%s\n", token.getBeginIndex(), token.getEndIndex(), token.getMorph(), token.getPos());
        }

    }
}