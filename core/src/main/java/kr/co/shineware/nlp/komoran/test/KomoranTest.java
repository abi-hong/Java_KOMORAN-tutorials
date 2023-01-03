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
package kr.co.shineware.nlp.komoran.test;

import kr.co.shineware.nlp.komoran.core.Komoran;
import kr.co.shineware.nlp.komoran.model.KomoranResult;
import kr.co.shineware.nlp.komoran.model.Token;

import java.util.List;

public class KomoranTest {

	public static void main(String[] args) throws Exception {

		// Komoran(): KOMORAN 생성자, 모델 경로를 인자로 받음
		Komoran komoran = new Komoran("models_full");
		komoran.setFWDic("user_data/fwd.user"); // setFWDic() : 기분식 사전 적용
		komoran.setUserDic("user_data/dic.user"); // setUserDic() : 사용자 사전 적용

		String input = "밀리언 달러 베이비랑 바람과 함께 사라지다랑 뭐가 더 재밌었어?";
		KomoranResult analyzeResultList = komoran.analyze(input);
		List<Token> tokenList = analyzeResultList.getTokenList();

		//print each tokens by getTokenList()
		System.out.println("==========print 'getTokenList()'==========");
		for (Token token : tokenList) {  // Token 객체 : 형태소, 품사 그리고 시작/끝 지점을 갖는 객체
			System.out.println(token);
			System.out.println(token.getMorph()+"/"+token.getPos()+"("+token.getBeginIndex()+","+token.getEndIndex()+")");
			System.out.println();
		}
		//print nouns
		System.out.println("==========print 'getNouns()'==========");
		System.out.println(analyzeResultList.getNouns());
		System.out.println();
		System.out.println("==========print 'getPlainText()'==========");
		System.out.println(analyzeResultList.getPlainText());
		System.out.println();
		System.out.println("==========print 'getList()'==========");
		System.out.println(analyzeResultList.getList());
	}
}