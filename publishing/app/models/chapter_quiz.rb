class ChapterQuiz < ActiveRecord::Base

  belongs_to :chapter
  has_many :chapter_questions_sets, -> { order(:position) }
  has_many :questions, through: :chapter_questions_sets
  validates :chapter, presence: true

  scope :for_short_uuid, ->(id) { where(["SUBSTRING(CAST(id AS VARCHAR), 1, 8) = ?", id]) }
  def self.find_by_uuid(id, with_404 = true)
    chapter_quizzes = for_short_uuid(id)
    raise ActiveRecord::RecordNotFound if chapter_quizzes.empty? && with_404
    raise StudyflowPublishing::ShortUuidDoubleError.new("Multiple chapter quizzes found for uuid: #{id}") if chapter_quizzes.length > 1
    chapter_quizzes.first
  end

  def to_param
    "#{id[0,8]}"
  end

  def to_s
    "#{chapter} - Chapter Quiz"
  end

  def to_publishing_format
    chapter_questions_sets.map(&:to_publishing_format)
  end

  #def errors_when_publishing
  #   errors = []
  #   errors << "No questions in the chapter quiz" if questions.active.empty?
  #   errors << questions.active.map(&:errors_when_publishing_for_entry_quiz)
  #   errors.flatten
  # end


end
