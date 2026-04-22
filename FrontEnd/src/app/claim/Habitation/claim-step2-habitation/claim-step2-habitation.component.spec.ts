import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ClaimStep2HabitationComponent } from './claim-step2-habitation.component';

describe('ClaimStep2HabitationComponent', () => {
  let component: ClaimStep2HabitationComponent;
  let fixture: ComponentFixture<ClaimStep2HabitationComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ClaimStep2HabitationComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(ClaimStep2HabitationComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
