package com.englishhero.vocabulary.learning;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import com.englishhero.identity.*;
import com.englishhero.reading.ArticleRules;
import com.englishhero.web.BusinessException;
import static com.englishhero.vocabulary.learning.WordbookDtos.*;
@Service
public class WordbookService {
 private final LearningMapper db;private final WordbookMapper books;private final IdentityService identities;
 public WordbookService(LearningMapper db,WordbookMapper books,IdentityService identities){this.db=db;this.books=books;this.identities=identities;}
 @Transactional(readOnly=true)public List<Book> list(SessionPrincipal actor){identities.current(actor);return books.books(actor.userId(),null);}
 @Transactional(readOnly=true)public Source source(SessionPrincipal actor){identities.current(actor);return books.selected(actor.userId());}
 @Transactional(isolation=Isolation.READ_COMMITTED)public Source select(SessionPrincipal actor,Selection input){identities.current(actor);long user=actor.userId();db.lock(user);if(input==null||!Set.of("PERSONAL","BOOK").contains(Objects.toString(input.kind(),"")))throw BusinessException.invalid("kind","请选择个人生词或词书");Long id=null;
  if(input.kind().equals("BOOK")){id=ArticleRules.id(input.bookId(),"bookId");var rows=books.books(user,id);if(rows.isEmpty()||(!rows.getFirst().status().equals("PUBLISHED")&&db.savedId(user,"NEW","BOOK:"+id)==null))throw new BusinessException(404,"WORD_BOOK_UNAVAILABLE","词书不存在或已下架");}else if(input.bookId()!=null)throw BusinessException.invalid("bookId","个人生词不需要词书编号");
  var prior=books.selected(user);boolean same=prior.kind().equals(input.kind())&&Objects.equals(prior.bookId(),id==null?null:id.toString());if(same)return prior;
  if(db.activeId(user)!=null)throw LearningService.conflict("请先退出并保存正在进行的轮次，再切换来源");db.jdbc().update("INSERT INTO vocabulary_learning_source(user_id,kind,book_id) VALUES(?,?,?) ON DUPLICATE KEY UPDATE kind=VALUES(kind),book_id=VALUES(book_id)",user,input.kind(),id);return books.selected(user);
 }
}
