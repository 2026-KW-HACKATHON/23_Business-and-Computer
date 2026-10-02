import "./NoteBox.css";

interface NoteBoxProps {
  /** 「박지은 학생의 메세지」 */
  title: string;
  body: string;
}

/** 회색 상자 안 짧은 글 (학생 한마디 등) */
function NoteBox({ title, body }: NoteBoxProps) {
  return (
    <div className="note-box">
      <p className="note-box__title">{title}</p>
      <p className="note-box__body">{body}</p>
    </div>
  );
}

export default NoteBox;
